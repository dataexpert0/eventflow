
package dev.eventflow.orders.application.usecase.cancel;

import dev.eventflow.orders.domain.event.OutboxEvent;
import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.repository.OrderRepository;
import dev.eventflow.orders.domain.repository.OutboxEventRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
class CancelOrderOutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    private CancelOrderUseCase useCase;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private OutboxEventRepository outboxRepository;

    private Order createPendingOrder() {
        Order order = new Order(UUID.randomUUID());

        return orderRepository.save(order);
    }

    @Test
    void shouldPersistCancellationAndOutboxEventTogether() {
        Order saved = createPendingOrder();

        Order cancelled = useCase.execute(saved.getId())
                .orElseThrow();

        String persistedStatus =
                jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = ?",
                        String.class,
                        saved.getId()
                );

        Integer eventCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM outbox_events
                        WHERE aggregate_id = ?
                          AND event_type = 'OrderCancelled'
                          AND published_at IS NULL
                        """,
                        Integer.class,
                        saved.getId()
                );

        String payloadStatus =
                jdbcTemplate.queryForObject(
                        """
                        SELECT payload ->> 'status'
                        FROM outbox_events
                        WHERE aggregate_id = ?
                          AND event_type = 'OrderCancelled'
                        """,
                        String.class,
                        saved.getId()
                );

        assertAll(
                () -> assertEquals(
                        "CANCELLED",
                        cancelled.getStatus().name()
                ),
                () -> assertEquals(
                        "CANCELLED",
                        persistedStatus
                ),
                () -> assertEquals(1, eventCount),
                () -> assertEquals(
                        "CANCELLED",
                        payloadStatus
                )
        );
    }

    @Test
    void shouldRollbackCancellationWhenOutboxFails() {
        Order saved = createPendingOrder();

        doThrow(new DataIntegrityViolationException(
                "Simulated outbox persistence failure"
        )).when(outboxRepository)
                .save(any(OutboxEvent.class));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> useCase.execute(saved.getId())
        );

        String persistedStatus =
                jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = ?",
                        String.class,
                        saved.getId()
                );

        Integer eventCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM outbox_events
                        WHERE aggregate_id = ?
                          AND event_type = 'OrderCancelled'
                        """,
                        Integer.class,
                        saved.getId()
                );

        assertAll(
                () -> assertEquals("PENDING", persistedStatus),
                () -> assertEquals(0, eventCount)
        );
    }
}
