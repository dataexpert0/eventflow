
package dev.eventflow.orders.infrastructure.persistence.outbox;

import dev.eventflow.orders.domain.event.OutboxEvent;
import dev.eventflow.orders.domain.repository.OutboxEventRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import java.time.Duration;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@Testcontainers
class JdbcOutboxEventRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldPersistPendingOutboxEvent() {
        UUID orderId = UUID.randomUUID();

        OutboxEvent event = OutboxEvent.pending(
                orderId,
                "OrderConfirmed",
                "{\"orderId\":\"" + orderId + "\"}"
        );

        repository.save(event);

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM outbox_events
                WHERE id = ?
                  AND aggregate_id = ?
                  AND event_type = ?
                  AND published_at IS NULL
                """,
                Integer.class,
                event.id(),
                orderId,
                "OrderConfirmed"
        );

        String payload = jdbcTemplate.queryForObject(
                """
                SELECT payload ->> 'orderId'
                FROM outbox_events
                WHERE id = ?
                """,
                String.class,
                event.id()
        );

        assertAll(
                () -> assertEquals(1, count),
                () -> assertEquals(
                        orderId.toString(),
                        payload
                )
        );
    }

    @Test
    void shouldRejectInvalidJsonPayload() {
        OutboxEvent event = OutboxEvent.pending(
                UUID.randomUUID(),
                "OrderConfirmed",
                "invalid-json"
        );

        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> repository.save(event)
        );
    }

    @Test
    void shouldFindPendingOutboxEvents() {
        jdbcTemplate.update("DELETE FROM outbox_events");

        UUID aggregateId = UUID.randomUUID();

        OutboxEvent first = new OutboxEvent(
                UUID.randomUUID(),
                aggregateId,
                "OrderCreated",
                "{\"status\":\"PENDING\"}",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2),
                null
        );

        OutboxEvent second = new OutboxEvent(
                UUID.randomUUID(),
                aggregateId,
                "OrderConfirmed",
                "{\"status\":\"CONFIRMED\"}",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                null
        );

        repository.save(first);
        repository.save(second);

        List<OutboxEvent> events =
                repository.findPending(10);

        assertAll(
                () -> assertEquals(2, events.size()),
                () -> assertEquals(first.id(), events.get(0).id()),
                () -> assertEquals(second.id(), events.get(1).id()),
                () -> assertTrue(
                        events.stream().noneMatch(
                                OutboxEvent::isPublished
                        )
                )
        );
    }

    @Test
    void shouldRespectPendingEventsLimit() {
        jdbcTemplate.update("DELETE FROM outbox_events");

        for (int i = 0; i < 3; i++) {
            repository.save(OutboxEvent.pending(
                    UUID.randomUUID(),
                    "OrderCreated",
                    "{\"status\":\"PENDING\"}"
            ));
        }

        List<OutboxEvent> events =
                repository.findPending(2);

        assertEquals(2, events.size());
    }

    @Test
    void shouldIgnorePublishedEvents() {
        jdbcTemplate.update("DELETE FROM outbox_events");

        OutboxEvent pending = OutboxEvent.pending(
                UUID.randomUUID(),
                "OrderCreated",
                "{\"status\":\"PENDING\"}"
        );

        OutboxEvent published = OutboxEvent.pending(
                UUID.randomUUID(),
                "OrderConfirmed",
                "{\"status\":\"CONFIRMED\"}"
        );

        repository.save(pending);
        repository.save(published);

        jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET published_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                published.id()
        );

        List<OutboxEvent> events =
                repository.findPending(10);

        assertAll(
                () -> assertEquals(1, events.size()),
                () -> assertEquals(
                        pending.id(),
                        events.get(0).id()
                )
        );
    }

    @Test
    void shouldClaimDifferentEventsForDifferentWorkers() {
        jdbcTemplate.update("DELETE FROM outbox_events");

        for (int i = 0; i < 4; i++) {
            repository.save(OutboxEvent.pending(
                    UUID.randomUUID(),
                    "OrderCreated",
                    "{\"status\":\"PENDING\"}"
            ));
        }

        UUID workerA = UUID.randomUUID();
        UUID workerB = UUID.randomUUID();

        List<OutboxEvent> firstBatch =
                repository.claimPending(
                        workerA, 2, Duration.ofMinutes(5)
                );

        List<OutboxEvent> secondBatch =
                repository.claimPending(
                        workerB, 2, Duration.ofMinutes(5)
                );

        var firstIds = firstBatch.stream()
                .map(OutboxEvent::id)
                .collect(java.util.stream.Collectors.toSet());

        var secondIds = secondBatch.stream()
                .map(OutboxEvent::id)
                .collect(java.util.stream.Collectors.toSet());

        assertAll(
                () -> assertEquals(2, firstBatch.size()),
                () -> assertEquals(2, secondBatch.size()),
                () -> assertTrue(
                        java.util.Collections.disjoint(
                                firstIds, secondIds
                        )
                )
        );
    }

    @Test
    void shouldNotClaimEventsWithActiveLease() {
        jdbcTemplate.update("DELETE FROM outbox_events");

        OutboxEvent event = OutboxEvent.pending(
                UUID.randomUUID(),
                "OrderCreated",
                "{\"status\":\"PENDING\"}"
        );

        repository.save(event);

        List<OutboxEvent> first =
                repository.claimPending(
                        UUID.randomUUID(),
                        1,
                        Duration.ofMinutes(5)
                );

        List<OutboxEvent> second =
                repository.claimPending(
                        UUID.randomUUID(),
                        1,
                        Duration.ofMinutes(5)
                );

        assertAll(
                () -> assertEquals(1, first.size()),
                () -> assertTrue(second.isEmpty())
        );
    }

    @Test
    void shouldReclaimEventAfterLeaseExpires() {
        jdbcTemplate.update("DELETE FROM outbox_events");

        OutboxEvent event = OutboxEvent.pending(
                UUID.randomUUID(),
                "OrderCreated",
                "{\"status\":\"PENDING\"}"
        );

        repository.save(event);

        UUID workerA = UUID.randomUUID();
        UUID workerB = UUID.randomUUID();

        repository.claimPending(
                workerA, 1, Duration.ofMinutes(5)
        );

        // Simula uma reserva expirada.
        jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET claimed_until =
                    CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = ?
                """,
                event.id()
        );

        List<OutboxEvent> reclaimed =
                repository.claimPending(
                        workerB, 1, Duration.ofMinutes(5)
                );

        UUID claimedBy = jdbcTemplate.queryForObject(
                """
                SELECT claimed_by
                FROM outbox_events
                WHERE id = ?
                """,
                UUID.class,
                event.id()
        );

        assertAll(
                () -> assertEquals(1, reclaimed.size()),
                () -> assertEquals(
                        event.id(),
                        reclaimed.get(0).id()
                ),
                () -> assertEquals(workerB, claimedBy)
        );
    }

    @Test
    void shouldMarkClaimedEventAsPublished() {
        jdbcTemplate.update("DELETE FROM outbox_events");

        OutboxEvent event = OutboxEvent.pending(
                UUID.randomUUID(),
                "OrderCreated",
                "{\"status\":\"PENDING\"}"
        );

        repository.save(event);

        UUID workerId = UUID.randomUUID();

        repository.claimPending(
                workerId,
                1,
                Duration.ofMinutes(5)
        );

        boolean marked = repository.markPublished(
                event.id(),
                workerId
        );

        Integer publishedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM outbox_events
                WHERE id = ?
                  AND published_at IS NOT NULL
                  AND claimed_by IS NULL
                  AND claimed_until IS NULL
                """,
                Integer.class,
                event.id()
        );

        assertAll(
                () -> assertTrue(marked),
                () -> assertEquals(1, publishedCount),
                () -> assertTrue(repository.findPending(10).isEmpty())
        );
    }

    @Test
    void shouldRejectPublicationByAnotherWorker() {
        jdbcTemplate.update("DELETE FROM outbox_events");

        OutboxEvent event = OutboxEvent.pending(
                UUID.randomUUID(),
                "OrderCreated",
                "{\"status\":\"PENDING\"}"
        );

        repository.save(event);

        UUID ownerId = UUID.randomUUID();
        UUID otherWorkerId = UUID.randomUUID();

        repository.claimPending(
                ownerId,
                1,
                Duration.ofMinutes(5)
        );

        boolean marked = repository.markPublished(
                event.id(),
                otherWorkerId
        );

        UUID actualOwner = jdbcTemplate.queryForObject(
                """
                SELECT claimed_by
                FROM outbox_events
                WHERE id = ?
                """,
                UUID.class,
                event.id()
        );

        Integer unpublishedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM outbox_events
                WHERE id = ?
                  AND published_at IS NULL
                """,
                Integer.class,
                event.id()
        );

        assertAll(
                () -> assertFalse(marked),
                () -> assertEquals(ownerId, actualOwner),
                () -> assertEquals(1, unpublishedCount)
        );
    }

    @Test
    void shouldRejectPublicationAfterLeaseExpires() {
        jdbcTemplate.update("DELETE FROM outbox_events");

        OutboxEvent event = OutboxEvent.pending(
                UUID.randomUUID(),
                "OrderCreated",
                "{\"status\":\"PENDING\"}"
        );

        repository.save(event);

        UUID workerId = UUID.randomUUID();

        repository.claimPending(
                workerId,
                1,
                Duration.ofMinutes(5)
        );

        // Simulate an expired lease.
        jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET claimed_until =
                    CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = ?
                """,
                event.id()
        );

        boolean marked = repository.markPublished(
                event.id(),
                workerId
        );

        Integer unpublishedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM outbox_events
                WHERE id = ?
                  AND published_at IS NULL
                """,
                Integer.class,
                event.id()
        );

        assertAll(
                () -> assertFalse(marked),
                () -> assertEquals(1, unpublishedCount)
        );
    }

    @Test
    void shouldSkipRowsLockedByAnotherTransaction()
            throws Exception {

        jdbcTemplate.update("DELETE FROM outbox_events");

        UUID aggregateId = UUID.randomUUID();

        OutboxEvent first = new OutboxEvent(
                UUID.randomUUID(),
                aggregateId,
                "OrderCreated",
                "{\"status\":\"PENDING\"}",
                OffsetDateTime.now(ZoneOffset.UTC)
                        .minusMinutes(2),
                null
        );

        OutboxEvent second = new OutboxEvent(
                UUID.randomUUID(),
                aggregateId,
                "OrderConfirmed",
                "{\"status\":\"CONFIRMED\"}",
                OffsetDateTime.now(ZoneOffset.UTC)
                        .minusMinutes(1),
                null
        );

        repository.save(first);
        repository.save(second);

        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Future<?> lockingTransaction = executor.submit(() -> {
                TransactionTemplate transaction =
                        new TransactionTemplate(transactionManager);

                transaction.executeWithoutResult(status -> {
                    // Keep the first event locked.
                    jdbcTemplate.queryForObject(
                            """
                            SELECT id
                            FROM outbox_events
                            WHERE id = ?
                            FOR UPDATE
                            """,
                            UUID.class,
                            first.id()
                    );

                    rowLocked.countDown();

                    try {
                        if (!releaseLock.await(
                                15, TimeUnit.SECONDS
                        )) {
                            throw new IllegalStateException(
                                    "Timed out waiting to release lock"
                            );
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                                "Locking transaction interrupted", e
                        );
                    }
                });
            });

            assertTrue(
                    rowLocked.await(10, TimeUnit.SECONDS),
                    "First transaction did not acquire the lock"
            );

            UUID workerB = UUID.randomUUID();

            Future<List<OutboxEvent>> claimFuture =
                    executor.submit(() ->
                            repository.claimPending(
                                    workerB,
                                    1,
                                    Duration.ofMinutes(5)
                            )
                    );

            List<OutboxEvent> claimed =
                    claimFuture.get(10, TimeUnit.SECONDS);

            assertAll(
                    () -> assertEquals(1, claimed.size()),
                    () -> assertEquals(
                            second.id(),
                            claimed.get(0).id()
                    )
            );

            releaseLock.countDown();

            lockingTransaction.get(10, TimeUnit.SECONDS);

        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
        }
    }

}
