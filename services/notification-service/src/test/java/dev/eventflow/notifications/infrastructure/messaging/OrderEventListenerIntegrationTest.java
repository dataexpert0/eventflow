
package dev.eventflow.notifications.infrastructure.messaging;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class OrderEventListenerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq =
            new RabbitMQContainer("rabbitmq:4-management");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private OrderEventListener listener;

    @BeforeAll
    static void configureDeadLetterPolicy() throws Exception {
        var result = rabbitmq.execInContainer(
                "rabbitmqctl",
                "set_policy",
                "--apply-to",
                "queues",
                "eventflow-notifications-dlx",
                "^eventflow\\.notifications\\.orders\\.q$",
                """
                {
                    "dead-letter-exchange":
                        "eventflow.notifications.dlx",
                    "dead-letter-routing-key":
                        "notifications.orders.dead"
                }
                """
        );

        assertEquals(
                0,
                result.getExitCode(),
                result.getStderr()
        );
    }

    private void sendEvent(
            String eventId,
            String eventType
    ) {
        MessageProperties properties =
                new MessageProperties();

        properties.setMessageId(eventId);
        properties.setHeader("eventType", eventType);
        properties.setContentType(
                MessageProperties.CONTENT_TYPE_JSON
        );

        Message message = new Message(
                "{\"status\":\"PENDING\"}"
                        .getBytes(StandardCharsets.UTF_8),
                properties
        );

        rabbitTemplate.send(
                NotificationRabbitConfiguration.EXCHANGE,
                "orders.created",
                message
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

    @Test
    void shouldProcessDuplicateEventOnlyOnce() {
        UUID eventId = UUID.randomUUID();

        sendEvent(eventId.toString(), "OrderCreated");
        sendEvent(eventId.toString(), "OrderCreated");

        // Confirm that the listener received both deliveries.
        verify(listener, timeout(10000).times(2))
                .receive(any(Message.class));

        // Both deliveries share the same event ID.
        assertEquals(1, countEvents(eventId));
    }

    @Test
    void shouldRouteInvalidEventToDeadLetterQueue() {
        String invalidId = "invalid-event-id";

        sendEvent(invalidId, "OrderCreated");

        Message deadLetter = rabbitTemplate.receive(
                NotificationRabbitConfiguration.DLQ,
                10000
        );

        assertNotNull(
                deadLetter,
                "Invalid message should reach the DLQ"
        );

        assertEquals(
                invalidId,
                deadLetter.getMessageProperties()
                        .getMessageId()
        );
    }
}