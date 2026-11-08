
package dev.eventflow.orders.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfiguration {

    public static final String EXCHANGE =
            "eventflow.orders.events";

    public static final String QUEUE =
            "eventflow.orders.events.q";

    public static final String ROUTING_PATTERN =
            "orders.*";

    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue orderEventsQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding orderEventsBinding(
            Queue orderEventsQueue,
            TopicExchange orderEventsExchange
    ) {
        return BindingBuilder
                .bind(orderEventsQueue)
                .to(orderEventsExchange)
                .with(ROUTING_PATTERN);
    }
}