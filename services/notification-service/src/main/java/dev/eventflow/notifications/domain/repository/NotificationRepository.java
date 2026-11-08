
package dev.eventflow.notifications.domain.repository;

import dev.eventflow.notifications.domain.model.Notification;
import dev.eventflow.notifications.domain.model.NotificationSummary;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository {

    void save(Notification notification);

    List<NotificationSummary> findByOrderId(
            UUID orderId,
            int limit,
            long offset
    );

    long countByOrderId(UUID orderId);
}
