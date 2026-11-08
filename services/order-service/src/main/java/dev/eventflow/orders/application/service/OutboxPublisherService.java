
package dev.eventflow.orders.application.service;

import dev.eventflow.orders.application.port.EventPublisher;
import dev.eventflow.orders.domain.event.OutboxEvent;
import dev.eventflow.orders.domain.repository.OutboxEventRepository;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class OutboxPublisherService {

    private final OutboxEventRepository repository;
    private final EventPublisher publisher;
    private final UUID workerId;
    private final Duration lease;

    public OutboxPublisherService(
            OutboxEventRepository repository,
            EventPublisher publisher,
            UUID workerId,
            Duration lease
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "Outbox repository is required"
        );

        this.publisher = Objects.requireNonNull(
                publisher,
                "Event publisher is required"
        );

        this.workerId = Objects.requireNonNull(
                workerId,
                "Worker ID is required"
        );

        this.lease = Objects.requireNonNull(
                lease,
                "Lease duration is required"
        );

        if (lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException(
                    "Lease must be positive"
            );
        }
    }

    public int processBatch(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "Batch limit must be greater than zero"
            );
        }

        List<OutboxEvent> events = repository.claimPending(
                workerId,
                limit,
                lease
        );

        int published = 0;

        for (OutboxEvent event : events) {
            publisher.publish(event);

            boolean marked = repository.markPublished(
                    event.id(),
                    workerId
            );

            if (!marked) {
                throw new IllegalStateException(
                        "Failed to mark event as published: "
                                + event.id()
                );
            }

            published++;
        }

        return published;
    }
}