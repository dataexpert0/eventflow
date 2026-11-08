
package dev.eventflow.notifications.application.usecase;

import dev.eventflow.notifications.domain.model.Notification;
import dev.eventflow.notifications.domain.repository.NotificationRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
class ProcessOrderEventIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    private ProcessOrderEventUseCase useCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private NotificationRepository notificationRepository;

    @AfterEach
    void tearDown() {
        reset(notificationRepository);
    }

    @Test
    void shouldPersistEventAndNotificationAtomically() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        boolean result = useCase.execute(
                eventId,
                "OrderCreated",
                orderId
        );

        assertAll(
                () -> assertTrue(result),
                () -> assertEquals(1, countProcessedEvents(eventId)),
                () -> assertEquals(1, countNotifications(eventId))
        );
    }

    @Test
    void shouldIgnoreDuplicateWithoutCreatingNotification() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        boolean first = useCase.execute(
                eventId,
                "OrderConfirmed",
                orderId
        );

        boolean second = useCase.execute(
                eventId,
                "OrderConfirmed",
                orderId
        );

        assertAll(
                () -> assertTrue(first),
                () -> assertFalse(second),
                () -> assertEquals(1, countProcessedEvents(eventId)),
                () -> assertEquals(1, countNotifications(eventId))
        );

        verify(notificationRepository, times(1))
                .save(any(Notification.class));
    }

    @Test
    void shouldRollbackEventWhenNotificationPersistenceFails() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        doThrow(new IllegalStateException(
                "Simulated notification failure"
        )).when(notificationRepository)
                .save(any(Notification.class));

        assertThrows(
                IllegalStateException.class,
                () -> useCase.execute(
                        eventId,
                        "OrderCancelled",
                        orderId
                )
        );

        assertAll(
                () -> assertEquals(0, countProcessedEvents(eventId)),
                () -> assertEquals(0, countNotifications(eventId))
        );

        reset(notificationRepository);

        boolean retried = useCase.execute(
                eventId,
                "OrderCancelled",
                orderId
        );

        assertAll(
                () -> assertTrue(retried),
                () -> assertEquals(1, countProcessedEvents(eventId)),
                () -> assertEquals(1, countNotifications(eventId))
        );
    }

    private int countProcessedEvents(UUID eventId) {
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

    private int countNotifications(UUID eventId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM notifications
                WHERE event_id = ?
                """,
                Integer.class,
                eventId
        );

        return count;
    }
}