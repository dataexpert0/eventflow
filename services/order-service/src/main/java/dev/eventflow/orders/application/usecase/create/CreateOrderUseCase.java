
package dev.eventflow.orders.application.usecase.create;

import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.model.OrderItem;
import dev.eventflow.orders.domain.repository.OrderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;

    public CreateOrderUseCase(
            OrderRepository orderRepository
    ) {
        this.orderRepository = orderRepository;
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

        return orderRepository.save(order);
    }
}