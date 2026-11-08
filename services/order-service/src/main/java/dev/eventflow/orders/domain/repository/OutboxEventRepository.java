
package dev.eventflow.orders.domain.repository;

import dev.eventflow.orders.domain.event.OutboxEvent;

import java.util.List;
import java.util.UUID;
import java.time.Duration;

public interface OutboxEventRepository {

    void save(OutboxEvent event);

    List<OutboxEvent> findPending(int limit);

    List<OutboxEvent> claimPending(
            UUID workerId,
            int limit,
            Duration lease
    );

    boolean markPublished(
            UUID eventId,
            UUID workerId
    );

}