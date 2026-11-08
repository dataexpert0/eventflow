
package dev.eventflow.orders.application.usecase.confirm;

import dev.eventflow.orders.domain.event.OutboxEvent;
import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.repository.OrderRepository;
import dev.eventflow.orders.domain.repository.OutboxEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConfirmOrderUseCase {

    private final OrderRepository repository;
    private final OutboxEventRepository outboxRepository;

    public ConfirmOrderUseCase(
            OrderRepository repository,
            OutboxEventRepository outboxRepository
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
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

        Order saved = repository.save(order);

        String payload = """
                {
                    "orderId": "%s",
                    "status": "CONFIRMED"
                }
                """.formatted(saved.getId());

        OutboxEvent event = OutboxEvent.pending(
                saved.getId(),
                "OrderConfirmed",
                payload
        );

        outboxRepository.save(event);

        return Optional.of(saved);
    }
}