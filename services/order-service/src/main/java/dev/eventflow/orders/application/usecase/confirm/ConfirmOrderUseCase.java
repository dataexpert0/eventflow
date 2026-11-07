
package dev.eventflow.orders.application.usecase.confirm;

import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.repository.OrderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConfirmOrderUseCase {

    private final OrderRepository repository;

    public ConfirmOrderUseCase(OrderRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Transactional
    public Optional<Order> execute(UUID orderId) {
        Objects.requireNonNull(orderId, "Order ID is required");

        Optional<Order> existing = repository.findById(orderId);

        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Order order = existing.get();

        order.confirm();

        return Optional.of(repository.save(order));
    }
}
