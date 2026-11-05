
package dev.eventflow.orders.application.usecase.get;

import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.repository.OrderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class GetOrderUseCase {

    private final OrderRepository orderRepository;

    public GetOrderUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Order> execute(UUID orderId) {
        Objects.requireNonNull(
                orderId, "Order ID is required"
        );

        return orderRepository.findById(orderId);
    }
}