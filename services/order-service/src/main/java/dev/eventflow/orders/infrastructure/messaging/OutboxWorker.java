
package dev.eventflow.orders.infrastructure.messaging;

import dev.eventflow.orders.application.service.OutboxPublisherService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

public class OutboxWorker {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxWorker.class);

    private final OutboxPublisherService service;
    private final int batchSize;

    public OutboxWorker(
            OutboxPublisherService service,
            int batchSize
    ) {
        this.service = Objects.requireNonNull(
                service,
                "Outbox publisher service is required"
        );

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "Batch size must be positive"
            );
        }

        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString =
                    "${eventflow.outbox.poll-interval-ms:5000}",
            initialDelayString =
                    "${eventflow.outbox.initial-delay-ms:5000}"
    )
    public void processPendingEvents() {
        try {
            int published = service.processBatch(batchSize);

            if (published > 0) {
                log.info(
                        "Outbox worker published {} events",
                        published
                );
            }

        } catch (RuntimeException exception) {
            log.error(
                    "Outbox worker failed to process batch",
                    exception
            );
        }
    }
}