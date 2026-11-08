
package dev.eventflow.orders.infrastructure.messaging;

import dev.eventflow.orders.application.port.EventPublisher;
import dev.eventflow.orders.domain.event.OutboxEvent;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class RabbitMqEventPublisherIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq =
            new RabbitMQContainer("rabbitmq:4-management");

    @Autowired
    private EventPublisher publisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private Binding orderEventsBinding;

    @Test
    void shouldPublishOrderCreatedToRabbitMq() {
        UUID orderId = UUID.randomUUID();

        String payload = """
                {
                    "orderId": "%s",
                    "status": "PENDING"
                }
                """.formatted(orderId);

        OutboxEvent event = OutboxEvent.pending(
                orderId,
                "OrderCreated",
                payload
        );

        publisher.publish(event);

        Message received = rabbitTemplate.receive(
                RabbitMqConfiguration.QUEUE,
                5000
        );

        assertNotNull(received);

        String receivedPayload = new String(
                received.getBody(),
                StandardCharsets.UTF_8
        );

        assertAll(
                () -> assertEquals(
                        payload,
                        receivedPayload
                ),
                () -> assertEquals(
                        event.id().toString(),
                        received.getMessageProperties()
                                .getMessageId()
                ),
                () -> assertEquals(
                        "OrderCreated",
                        received.getMessageProperties()
                                .getHeader("eventType")
                ),
                () -> assertEquals(
                        orderId.toString(),
                        received.getMessageProperties()
                                .getHeader("aggregateId")
                )
        );
    }

    @Test
    void shouldRejectUnroutableMessage() {
        UUID orderId = UUID.randomUUID();

        OutboxEvent event = OutboxEvent.pending(
                orderId,
                "OrderCreated",
                """
                {
                    "status": "PENDING"
                }
                """
        );

        // Temporarily remove the exchange-to-queue binding.
        amqpAdmin.removeBinding(orderEventsBinding);

        try {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> publisher.publish(event)
            );

            assertTrue(
                    exception.getMessage().contains(
                            "could not route event"
                    )
            );
        } finally {
            // Restore the broker topology even if the test fails.
            amqpAdmin.declareBinding(orderEventsBinding);
        }
    }

}
