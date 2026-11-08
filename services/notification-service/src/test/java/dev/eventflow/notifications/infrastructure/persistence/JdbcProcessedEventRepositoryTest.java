
package dev.eventflow.notifications.infrastructure.persistence;

import dev.eventflow.notifications.domain.repository.ProcessedEventRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
class JdbcProcessedEventRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    private ProcessedEventRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldRegisterEventOnlyOnce() {
        UUID eventId = UUID.randomUUID();

        boolean first = repository.registerIfAbsent(
                eventId,
                "OrderCreated"
        );

        boolean second = repository.registerIfAbsent(
                eventId,
                "OrderCreated"
        );

        Integer count = countEvents(eventId);

        assertAll(
                () -> assertTrue(first),
                () -> assertFalse(second),
                () -> assertEquals(1, count)
        );
    }

    @Test
    void shouldRegisterDifferentEventsIndependently() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        boolean first = repository.registerIfAbsent(
                firstId,
                "OrderCreated"
        );

        boolean second = repository.registerIfAbsent(
                secondId,
                "OrderConfirmed"
        );

        assertAll(
                () -> assertTrue(first),
                () -> assertTrue(second),
                () -> assertEquals(1, countEvents(firstId)),
                () -> assertEquals(1, countEvents(secondId))
        );
    }

    @Test
    void shouldAllowRetryAfterTransactionRollback() {
        UUID eventId = UUID.randomUUID();

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        assertThrows(
                IllegalStateException.class,
                () -> transaction.execute(status -> {
                    boolean inserted =
                            repository.registerIfAbsent(
                                    eventId,
                                    "OrderCreated"
                            );

                    assertTrue(inserted);

                    throw new IllegalStateException(
                            "Simulated processing failure"
                    );
                })
        );

        // The insert must have been rolled back.
        assertEquals(0, countEvents(eventId));

        // A later delivery must be accepted.
        boolean retried = repository.registerIfAbsent(
                eventId,
                "OrderCreated"
        );

        assertAll(
                () -> assertTrue(retried),
                () -> assertEquals(1, countEvents(eventId))
        );
    }

    private int countEvents(UUID eventId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM processed_events
                WHERE event_id = ?
                """,
                Integer.class,
                eventId
        );

        return count;
    }
}
