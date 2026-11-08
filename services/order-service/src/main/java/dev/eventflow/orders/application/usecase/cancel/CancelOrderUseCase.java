
package dev.eventflow.orders.application.usecase.cancel;

import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.repository.OrderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import dev.eventflow.orders.domain.event.OutboxEvent;
import dev.eventflow.orders.domain.repository.OutboxEventRepository;

@Service
public class CancelOrderUseCase {

    private final OrderRepository repository;
    private final OutboxEventRepository outboxRepository;

    public CancelOrderUseCase(OrderRepository repository, OutboxEventRepository outboxRepository) {
        this.repository = Objects.requireNonNull(
                repository,
                "Order repository is required"
        );

        this.outboxRepository = Objects.requireNonNull(
                outboxRepository,
                "Outbox repository is required"
        );
    }

    @Transactional
    public Optional<Order> execute(UUID orderId) {
        Objects.requireNonNull(orderId, "Order ID is required");

        Optional<Order> existing = repository.findById(orderId);

        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Order order = existing.get();

        order.cancel();

        Order saved = repository.save(order);

        String payload = """
        {
            "orderId": "%s",
            "status": "CANCELLED"
        }
        """.formatted(saved.getId());

        OutboxEvent event = OutboxEvent.pending(
                saved.getId(),
                "OrderCancelled",
                payload
        );

        outboxRepository.save(event);

        return Optional.of(saved);
    }
}