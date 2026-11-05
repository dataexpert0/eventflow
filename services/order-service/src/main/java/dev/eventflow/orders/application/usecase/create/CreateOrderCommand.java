package dev.eventflow.orders.application.usecase.create;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CreateOrderCommand(UUID customerId, List<CreateOrderItemCommand> items) {

    public CreateOrderCommand
    {
        Objects.requireNonNull(customerId, "Customer ID is required");

        items = List.copyOf(Objects.requireNonNull(items, "Order items are required"));
    }
}
