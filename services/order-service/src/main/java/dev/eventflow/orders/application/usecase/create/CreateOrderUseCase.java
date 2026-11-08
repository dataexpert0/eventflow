
package dev.eventflow.orders.application.usecase.create;

import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.model.OrderItem;
import dev.eventflow.orders.domain.repository.OrderRepository;
import dev.eventflow.orders.domain.event.OutboxEvent;
import dev.eventflow.orders.domain.repository.OutboxEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;

    public CreateOrderUseCase(
            OrderRepository orderRepository,
            OutboxEventRepository outboxRepository
    ) {
        this.orderRepository = Objects.requireNonNull(
                orderRepository,
                "Order repository is required"
        );

        this.outboxRepository = Objects.requireNonNull(
                outboxRepository,
                "Outbox repository is required"
        );
    }

    @Transactional
    public Order execute(CreateOrderCommand command) {
        Objects.requireNonNull(
                command, "Create order command is required"
        );

        if (command.items().isEmpty()) {
            throw new IllegalArgumentException(
                    "Order must contain at least one item"
            );
        }

        Order order = new Order(command.customerId());

        for (CreateOrderItemCommand item : command.items()) {
            order.addItem(new OrderItem(
                    item.productId(),
                    item.quantity(),
                    item.unitPrice()
            ));
        }

        Order saved = orderRepository.save(order);

        String payload = """
            {
                "orderId": "%s",
                "customerId": "%s",
                "status": "PENDING"
            }
            """.formatted(
                saved.getId(),
                saved.getCustomerId()
        );

        OutboxEvent event = OutboxEvent.pending(
                saved.getId(),
                "OrderCreated",
                payload
        );

        outboxRepository.save(event);

        return saved;
    }
}