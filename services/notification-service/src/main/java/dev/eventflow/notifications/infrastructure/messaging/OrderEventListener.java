
package dev.eventflow.notifications.infrastructure.messaging;

import dev.eventflow.notifications.application.usecase.ProcessOrderEventUseCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;
import java.util.UUID;

@Component
public class OrderEventListener {

    private static final Logger log =
            LoggerFactory.getLogger(OrderEventListener.class);

    private final ProcessOrderEventUseCase useCase;
    private final JsonMapper jsonMapper;

    public OrderEventListener(
            ProcessOrderEventUseCase useCase,
            JsonMapper jsonMapper
    ) {
        this.useCase = Objects.requireNonNull(useCase);
        this.jsonMapper = Objects.requireNonNull(jsonMapper);
    }

    @RabbitListener(
            queues = NotificationRabbitConfiguration.QUEUE
    )
    public void receive(Message message) {
        String eventId = message.getMessageProperties()
                .getMessageId();

        Object eventTypeHeader = message.getMessageProperties()
                .getHeader("eventType");

        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing event ID"
            );
        }

        if (!(eventTypeHeader instanceof String eventType)
                || eventType.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing event type"
            );
        }

        UUID eventUuid = UUID.fromString(eventId);

        JsonNode payload = jsonMapper.readTree(message.getBody());

        if (!payload.isObject()
                || !payload.hasNonNull("orderId")) {
            throw new IllegalArgumentException(
                    "Missing order ID in event payload"
            );
        }

        UUID orderId = UUID.fromString(
                payload.get("orderId").asString()
        );

        boolean processed = useCase.execute(
                eventUuid,
                eventType,
                orderId
        );

        if (processed) {
            log.info(
                    "Notification created: eventId={}, orderId={}",
                    eventUuid,
                    orderId
            );
        } else {
            log.info(
                    "Duplicate event ignored: id={}",
                    eventUuid
            );
        }
    }
}