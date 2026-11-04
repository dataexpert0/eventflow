package dev.eventflow.orders.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Order {
    private final UUID id;
    private final UUID customerId;
    private final List<OrderItem> items;

    private OrderStatus status;

    // Declarando o construtor
    public Order(UUID customerId) {
        this.id = UUID.randomUUID();
        this.customerId = Objects.requireNonNull(customerId, "Customer ID is required!");
        this.items = new ArrayList<>();
        this.status = OrderStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public void addItem(OrderItem item) {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Cannot modify a non-pending order!");
        }
        items.add(Objects.requireNonNull(item));
    }

    public BigDecimal calculateTotal() {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void confirm() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Only pending orders can be confirmed!");
        }

        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot confirm an empty order!");
        }

        status = OrderStatus.CONFIRMED;
    }

    public void cancel() {
        if (status != OrderStatus.PENDING)
        {
            throw new IllegalStateException("Only pending orders can be cancelled!");
        }
        status = OrderStatus.CANCELLED;
    }
}
