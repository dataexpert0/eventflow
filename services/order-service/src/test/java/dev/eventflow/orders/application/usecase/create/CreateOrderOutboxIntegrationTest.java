
package dev.eventflow.orders.application.usecase.create;

import dev.eventflow.orders.domain.event.OutboxEvent;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
class CreateOrderOutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    private CreateOrderUseCase useCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private OutboxEventRepository outboxRepository;

    private CreateOrderCommand createCommand(UUID customerId) {
        return new CreateOrderCommand(
                customerId,
                List.of(new CreateOrderItemCommand(
                        UUID.randomUUID(),
                        2,
                        new BigDecimal("25.00")
                ))
        );
    }

    @Test
    void shouldPersistOrderAndCreatedEventTogether() {
        UUID customerId = UUID.randomUUID();

        var order = useCase.execute(
                createCommand(customerId)
        );

        Integer orderCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM orders
                WHERE id = ?
                  AND status = 'PENDING'
                """,
                Integer.class,
                order.getId()
        );

        Integer eventCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM outbox_events
                WHERE aggregate_id = ?
                  AND event_type = 'OrderCreated'
                  AND published_at IS NULL
                """,
                Integer.class,
                order.getId()
        );

        String eventCustomerId = jdbcTemplate.queryForObject(
                """
                SELECT payload ->> 'customerId'
                FROM outbox_events
                WHERE aggregate_id = ?
                  AND event_type = 'OrderCreated'
                """,
                String.class,
                order.getId()
        );

        assertAll(
                () -> assertEquals(1, orderCount),
                () -> assertEquals(1, eventCount),
                () -> assertEquals(
                        customerId.toString(),
                        eventCustomerId
                )
        );
    }

    @Test
    void shouldRollbackOrderCreationWhenOutboxFails() {
        UUID customerId = UUID.randomUUID();

        doThrow(new DataIntegrityViolationException(
                "Simulated outbox persistence failure"
        )).when(outboxRepository)
                .save(any(OutboxEvent.class));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> useCase.execute(
                        createCommand(customerId)
                )
        );

        Integer orderCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM orders
                WHERE customer_id = ?
                """,
                Integer.class,
                customerId
        );

        Integer eventCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM outbox_events
                WHERE event_type = 'OrderCreated'
                  AND payload ->> 'customerId' = ?
                """,
                Integer.class,
                customerId.toString()
        );

        assertAll(
                () -> assertEquals(0, orderCount),
                () -> assertEquals(0, eventCount)
        );
    }
}
