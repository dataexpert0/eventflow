
package dev.eventflow.notifications.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationSummary(
        UUID id,
        UUID eventId,
        UUID orderId,
        String message,
        OffsetDateTime createdAt
) {
}