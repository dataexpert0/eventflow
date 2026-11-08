
package dev.eventflow.orders.application.port;

import dev.eventflow.orders.domain.event.OutboxEvent;

public interface EventPublisher {

    void publish(OutboxEvent event);
}
