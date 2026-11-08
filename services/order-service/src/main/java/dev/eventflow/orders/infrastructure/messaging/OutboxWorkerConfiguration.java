
package dev.eventflow.orders.infrastructure.messaging;

import dev.eventflow.orders.application.port.EventPublisher;
import dev.eventflow.orders.application.service.OutboxPublisherService;
import dev.eventflow.orders.domain.repository.OutboxEventRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;
import java.util.UUID;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "eventflow.outbox",
        name = "enabled",
        havingValue = "true"
)
public class OutboxWorkerConfiguration {

    @Bean
    public OutboxPublisherService outboxPublisherService(
            OutboxEventRepository repository,
            EventPublisher publisher,
            @Value("${eventflow.outbox.lease:PT5M}")
            Duration lease
    ) {
        return new OutboxPublisherService(
                repository,
                publisher,
                UUID.randomUUID(),
                lease
        );
    }

    @Bean
    public OutboxWorker outboxWorker(
            OutboxPublisherService service,
            @Value("${eventflow.outbox.batch-size:10}")
            int batchSize
    ) {
        return new OutboxWorker(service, batchSize);
    }
}
