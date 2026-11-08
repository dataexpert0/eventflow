
package dev.eventflow.orders.domain.event;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        UUID aggregateId,
        String eventType,
        String payload,
        OffsetDateTime occurredAt,
        OffsetDateTime publishedAt
) {

    public OutboxEvent {
        Objects.requireNonNull(id, "Event ID is required");
        Objects.requireNonNull(
                aggregateId, "Aggregate ID is required"
        );
        Objects.requireNonNull(
                occurredAt, "Occurrence time is required"
        );

        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException(
                    "Event type is required"
            );
        }

        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException(
                    "Event payload is required"
            );
        }
    }

    public static OutboxEvent pending(
            UUID aggregateId,
            String eventType,
            String payload
    ) {
        return new OutboxEvent(
                UUID.randomUUID(),
                aggregateId,
                eventType,
                payload,
                OffsetDateTime.now(ZoneOffset.UTC),
                null
        );
    }

    public boolean isPublished() {
        return publishedAt != null;
    }
}