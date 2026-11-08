
package dev.eventflow.notifications.domain.model;

import java.util.Objects;
import java.util.UUID;

public record Notification(
        UUID id,
        UUID eventId,
        UUID orderId,
        String message
) {
    public Notification {
        Objects.requireNonNull(
                id, "Notification ID is required"
        );

        Objects.requireNonNull(
                eventId, "Event ID is required"
        );

        Objects.requireNonNull(
                orderId, "Order ID is required"
        );

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "Notification message is required"
            );
        }

        if (message.length() > 500) {
            throw new IllegalArgumentException(
                    "Notification message is too long"
            );
        }
    }

    public static Notification create(
            UUID eventId,
            UUID orderId,
            String message
    ) {
        return new Notification(
                UUID.randomUUID(),
                eventId,
                orderId,
                message
        );
    }
}