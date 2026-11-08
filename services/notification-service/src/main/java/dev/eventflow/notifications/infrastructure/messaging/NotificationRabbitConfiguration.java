
package dev.eventflow.notifications.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationRabbitConfiguration {

    public static final String EXCHANGE =
            "eventflow.orders.events";

    public static final String QUEUE =
            "eventflow.notifications.orders.q";

    public static final String ROUTING_PATTERN =
            "orders.*";

    public static final String DLX =
            "eventflow.notifications.dlx";

    public static final String DLQ =
            "eventflow.notifications.orders.dlq";

    public static final String DEAD_ROUTING_KEY =
            "notifications.orders.dead";

    @Bean
    public TopicExchange notificationOrderEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationOrdersQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding notificationOrdersBinding(
            Queue notificationOrdersQueue,
            TopicExchange notificationOrderEventsExchange
    ) {
        return BindingBuilder
                .bind(notificationOrdersQueue)
                .to(notificationOrderEventsExchange)
                .with(ROUTING_PATTERN);
    }

    @Bean
    public DirectExchange notificationDeadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return new Queue(DLQ, true);
    }

    @Bean
    public Binding notificationDeadLetterBinding(
            Queue notificationDeadLetterQueue,
            DirectExchange notificationDeadLetterExchange
    ) {
        return BindingBuilder
                .bind(notificationDeadLetterQueue)
                .to(notificationDeadLetterExchange)
                .with(DEAD_ROUTING_KEY);
    }
}