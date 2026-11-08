
package dev.eventflow.notifications.domain.repository;

import java.util.UUID;

public interface ProcessedEventRepository {

    boolean registerIfAbsent(
            UUID eventId,
            String eventType
    );
}
