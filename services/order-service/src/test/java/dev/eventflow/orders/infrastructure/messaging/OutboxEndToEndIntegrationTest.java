
package dev.eventflow.orders.infrastructure.messaging;

import dev.eventflow.orders.application.port.EventPublisher;
import dev.eventflow.orders.application.service.OutboxPublisherService;
import dev.eventflow.orders.application.usecase.create.CreateOrderCommand;
import dev.eventflow.orders.application.usecase.create.CreateOrderItemCommand;
import dev.eventflow.orders.application.usecase.create.CreateOrderUseCase;
import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.repository.OutboxEventRepository;

import org.junit.jupiter.api.Test;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "eventflow.outbox.enabled=false")
@Testcontainers
class OutboxEndToEndIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq =
            new RabbitMQContainer("rabbitmq:4-management");

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPublishOrderCreatedFromOutboxToRabbitMq() {
        UUID customerId = UUID.randomUUID();

        CreateOrderCommand command = new CreateOrderCommand(
                customerId,
                List.of(new CreateOrderItemCommand(
                        UUID.randomUUID(),
                        2,
                        new BigDecimal("25.00")
                ))
        );

        // 1. Create the order and its outbox event.
        Order order = createOrderUseCase.execute(command);

        Integer pendingBefore = jdbcTemplate.queryForObject(
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

        assertEquals(1, pendingBefore);

        // 2. Process the outbox batch.
        OutboxPublisherService service =
                new OutboxPublisherService(
                        outboxRepository,
                        eventPublisher,
                        UUID.randomUUID(),
                        Duration.ofMinutes(5)
                );

        int published = service.processBatch(10);

        assertEquals(1, published);

        // 3. Verify delivery to RabbitMQ.
        Message received = rabbitTemplate.receive(
                RabbitMqConfiguration.QUEUE,
                5000
        );

        assertNotNull(received);

        String payload = new String(
                received.getBody(),
                StandardCharsets.UTF_8
        );

        assertAll(
                () -> assertTrue(
                        payload.contains(
                                order.getId().toString()
                        )
                ),
                () -> assertEquals(
                        "OrderCreated",
                        received.getMessageProperties()
                                .getHeader("eventType")
                ),
                () -> assertEquals(
                        order.getId().toString(),
                        received.getMessageProperties()
                                .getHeader("aggregateId")
                )
        );

        // 4. Verify publication status in PostgreSQL.
        Integer publishedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM outbox_events
                WHERE aggregate_id = ?
                  AND event_type = 'OrderCreated'
                  AND published_at IS NOT NULL
                  AND claimed_by IS NULL
                  AND claimed_until IS NULL
                """,
                Integer.class,
                order.getId()
        );

        assertEquals(1, publishedCount);

        // 5. A published event must not be claimed again.
        assertEquals(0, service.processBatch(10));
    }
}