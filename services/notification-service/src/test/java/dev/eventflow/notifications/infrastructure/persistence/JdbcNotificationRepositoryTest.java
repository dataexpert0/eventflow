
package dev.eventflow.notifications.infrastructure.persistence;

import dev.eventflow.notifications.domain.model.Notification;
import dev.eventflow.notifications.domain.repository.NotificationRepository;
import dev.eventflow.notifications.domain.repository.ProcessedEventRepository;
import dev.eventflow.notifications.domain.model.NotificationSummary;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
class JdbcNotificationRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistNotification() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        processedEventRepository.registerIfAbsent(
                eventId,
                "OrderCreated"
        );

        Notification notification = Notification.create(
                eventId,
                orderId,
                "Order created successfully"
        );

        repository.save(notification);

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM notifications
                WHERE event_id = ?
                  AND order_id = ?
                """,
                Integer.class,
                eventId,
                orderId
        );

        assertEquals(1, count);
    }

    @Test
    void shouldRejectDuplicateNotificationForSameEvent() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        processedEventRepository.registerIfAbsent(
                eventId,
                "OrderCreated"
        );

        Notification first = Notification.create(
                eventId,
                orderId,
                "First notification"
        );

        Notification duplicate = Notification.create(
                eventId,
                orderId,
                "Duplicate notification"
        );

        repository.save(first);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.save(duplicate)
        );

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM notifications
                WHERE event_id = ?
                """,
                Integer.class,
                eventId
        );

        assertEquals(1, count);
    }

    @Test
    void shouldFindNotificationsOnlyForRequestedOrder() {
        UUID targetOrderId = UUID.randomUUID();
        UUID otherOrderId = UUID.randomUUID();

        UUID firstEventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();
        UUID otherEventId = UUID.randomUUID();

        processedEventRepository.registerIfAbsent(
                firstEventId, "OrderCreated"
        );
        processedEventRepository.registerIfAbsent(
                secondEventId, "OrderConfirmed"
        );
        processedEventRepository.registerIfAbsent(
                otherEventId, "OrderCreated"
        );

        repository.save(Notification.create(
                firstEventId,
                targetOrderId,
                "Order created successfully"
        ));

        repository.save(Notification.create(
                secondEventId,
                targetOrderId,
                "Order confirmed successfully"
        ));

        repository.save(Notification.create(
                otherEventId,
                otherOrderId,
                "Another order"
        ));

        var results = repository.findByOrderId(
                targetOrderId,
                100,
                0L
        );

        assertEquals(2, results.size());

        assertTrue(results.stream()
                .allMatch(notification ->
                        targetOrderId.equals(
                                notification.orderId()
                        )
                ));

        assertTrue(results.stream()
                .anyMatch(notification ->
                        firstEventId.equals(
                                notification.eventId()
                        )
                ));

        assertTrue(results.stream()
                .anyMatch(notification ->
                        secondEventId.equals(
                                notification.eventId()
                        )
                ));
    }

    @Test
    void shouldCountNotificationsOnlyForRequestedOrder() {
        UUID targetOrderId = UUID.randomUUID();
        UUID otherOrderId = UUID.randomUUID();

        UUID firstEventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();
        UUID otherEventId = UUID.randomUUID();

        processedEventRepository.registerIfAbsent(
                firstEventId, "OrderCreated"
        );
        processedEventRepository.registerIfAbsent(
                secondEventId, "OrderConfirmed"
        );
        processedEventRepository.registerIfAbsent(
                otherEventId, "OrderCreated"
        );

        repository.save(Notification.create(
                firstEventId,
                targetOrderId,
                "Order created"
        ));

        repository.save(Notification.create(
                secondEventId,
                targetOrderId,
                "Order confirmed"
        ));

        repository.save(Notification.create(
                otherEventId,
                otherOrderId,
                "Another order"
        ));

        assertEquals(
                2L,
                repository.countByOrderId(targetOrderId)
        );

        assertEquals(
                1L,
                repository.countByOrderId(otherOrderId)
        );
    }

    @Test
    void shouldPaginateNotificationsWithoutRepeatingRows() {
        UUID orderId = UUID.randomUUID();

        UUID firstEventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();
        UUID thirdEventId = UUID.randomUUID();

        for (UUID eventId : List.of(
                firstEventId,
                secondEventId,
                thirdEventId
        )) {
            processedEventRepository.registerIfAbsent(
                    eventId,
                    "OrderCreated"
            );

            repository.save(Notification.create(
                    eventId,
                    orderId,
                    "Order notification"
            ));
        }

        var firstPage = repository.findByOrderId(
                orderId, 2, 0L
        );

        var secondPage = repository.findByOrderId(
                orderId, 2, 2L
        );

        assertEquals(2, firstPage.size());
        assertEquals(1, secondPage.size());

        var allIds = new java.util.HashSet<UUID>();

        firstPage.forEach(
                notification -> allIds.add(notification.id())
        );

        secondPage.forEach(
                notification -> allIds.add(notification.id())
        );

        assertEquals(3, allIds.size());
    }
}