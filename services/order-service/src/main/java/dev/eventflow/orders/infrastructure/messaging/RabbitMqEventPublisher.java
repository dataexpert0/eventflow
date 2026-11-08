
package dev.eventflow.orders.infrastructure.messaging;

import dev.eventflow.orders.application.port.EventPublisher;
import dev.eventflow.orders.domain.event.OutboxEvent;

import org.jspecify.annotations.NonNull;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public class RabbitMqEventPublisher
        implements EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqEventPublisher(
            RabbitTemplate rabbitTemplate
    ) {
        this.rabbitTemplate = Objects.requireNonNull(
                rabbitTemplate,
                "RabbitTemplate is required"
        );
    }

    @Override
    public void publish(OutboxEvent event) {
        Objects.requireNonNull(event, "Event is required");

        String routingKey = switch (event.eventType()) {
            case "OrderCreated" -> "orders.created";
            case "OrderConfirmed" -> "orders.confirmed";
            case "OrderCancelled" -> "orders.cancelled";
            default -> throw new IllegalArgumentException(
                    "Unsupported event type: " + event.eventType()
            );
        };

        MessageProperties properties = createProperties(event);

        Message message = new Message(
                event.payload().getBytes(StandardCharsets.UTF_8),
                properties
        );

        CorrelationData correlation =
                new CorrelationData(event.id().toString());

        rabbitTemplate.send(
                RabbitMqConfiguration.EXCHANGE,
                routingKey,
                message,
                correlation
        );

        try {
            CorrelationData.Confirm confirm =
                    correlation.getFuture().get(
                            10,
                            TimeUnit.SECONDS
                    );


            if (!confirm.ack()) {
                throw new IllegalStateException(
                        "RabbitMQ rejected event: "
                                + event.id()
                                + ", reason: "
                                + confirm.reason()
                );
            }


            if (correlation.getReturned() != null) {
                throw new IllegalStateException(
                        "RabbitMQ could not route event: "
                                + event.id()
                );
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "RabbitMQ publishing interrupted",
                    e
            );

        } catch (java.util.concurrent.ExecutionException
                 | java.util.concurrent.TimeoutException e) {

            throw new IllegalStateException(
                    "RabbitMQ publishing confirmation failed",
                    e
            );
        }
    }

    private static @NonNull MessageProperties createProperties(OutboxEvent event) {
        MessageProperties properties = new MessageProperties();

        properties.setMessageId(event.id().toString());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setHeader("eventType", event.eventType());
        properties.setHeader(
                "aggregateId",
                event.aggregateId().toString()
        );
        return properties;
    }
}
