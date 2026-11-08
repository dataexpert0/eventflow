
package dev.eventflow.orders.application.usecase.confirm;

import dev.eventflow.orders.domain.event.OutboxEvent;
import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.model.OrderItem;
import dev.eventflow.orders.domain.model.OrderStatus;
import dev.eventflow.orders.domain.repository.OrderRepository;
import dev.eventflow.orders.domain.repository.OutboxEventRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
class ConfirmOrderOutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    private ConfirmOrderUseCase useCase;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private OutboxEventRepository outboxRepository;

    private Order createPendingOrder() {
        Order order = new Order(UUID.randomUUID());

        order.addItem(new OrderItem(
                UUID.randomUUID(),
                2,
                new BigDecimal("25.00")
        ));

        return orderRepository.save(order);
    }

    @Test
    void shouldPersistOrderAndOutboxEventTogether() {
        Order saved = createPendingOrder();

        Order confirmed = useCase.execute(saved.getId())
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
                          AND event_type = 'OrderConfirmed'
                          AND published_at IS NULL
                        """,
                        Integer.class,
                        saved.getId()
                );

        String payloadOrderId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT payload ->> 'orderId'
                        FROM outbox_events
                        WHERE aggregate_id = ?
                        """,
                        String.class,
                        saved.getId()
                );

        assertAll(
                () -> assertEquals(
                        OrderStatus.CONFIRMED,
                        confirmed.getStatus()
                ),
                () -> assertEquals(
                        "CONFIRMED",
                        persistedStatus
                ),
                () -> assertEquals(1, eventCount),
                () -> assertEquals(
                        saved.getId().toString(),
                        payloadOrderId
                )
        );
    }

    @Test
    void shouldRollbackOrderWhenOutboxPersistenceFails() {
        Order saved = createPendingOrder();

        // Simulate a persistence failure in the Outbox.
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
                        """,
                        Integer.class,
                        saved.getId()
                );

        assertAll(
                () -> assertEquals("PENDING", persistedStatus),
                () -> assertEquals(0, eventCount)
        );
    }

    @Test
    void shouldRollbackOutboxEventWhenTransactionFails() {
        Order saved = createPendingOrder();

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        assertThrows(
                IllegalStateException.class,
                () -> transaction.execute(status -> {
                    useCase.execute(saved.getId())
                            .orElseThrow();

                    Integer eventCount =
                            jdbcTemplate.queryForObject(
                                    """
                                    SELECT COUNT(*)
                                    FROM outbox_events
                                    WHERE aggregate_id = ?
                                      AND event_type = 'OrderConfirmed'
                                    """,
                                    Integer.class,
                                    saved.getId()
                            );

                    assertEquals(1, eventCount);

                    // The event was inserted, but the
                    // surrounding transaction has not committed.
                    throw new IllegalStateException(
                            "Simulated failure after outbox insertion"
                    );
                })
        );

        String persistedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?",
                String.class,
                saved.getId()
        );

        Integer persistedEvents = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM outbox_events
                WHERE aggregate_id = ?
                """,
                Integer.class,
                saved.getId()
        );

        assertAll(
                () -> assertEquals("PENDING", persistedStatus),
                () -> assertEquals(0, persistedEvents)
        );
    }
}
