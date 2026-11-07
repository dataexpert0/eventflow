
package dev.eventflow.orders.infrastructure.persistence.mapper;

import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.model.OrderItem;
import dev.eventflow.orders.infrastructure.persistence.entity.OrderEntity;
import dev.eventflow.orders.infrastructure.persistence.entity.OrderItemEntity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class OrderPersistenceMapper {

    public OrderEntity toEntity(Order order) {
        Objects.requireNonNull(order, "Order is required");

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        OrderEntity entity = new OrderEntity(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                now,
                now
        );

        for (OrderItem item : order.getItems()) {
            OrderItemEntity itemEntity = new OrderItemEntity(
                    UUID.randomUUID(),
                    item.productId(),
                    item.quantity(),
                    item.unitPrice()
            );

            entity.addItem(itemEntity);
        }

        return entity;
    }

    public Order toDomain(OrderEntity entity) {
        Objects.requireNonNull(entity, "Order entity is required");

        List<OrderItem> items = entity.getItems()
                .stream()
                .map(item -> new OrderItem(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getUnitPrice()
                ))
                .toList();


        if (entity.getVersion() == null) {
            return Order.restore(
                    entity.getId(),
                    entity.getCustomerId(),
                    entity.getStatus(),
                    items
            );
        }

        return Order.restore(
                entity.getId(),
                entity.getCustomerId(),
                entity.getStatus(),
                items,
                entity.getVersion()
        );
    }

    public void updateEntity(Order order, OrderEntity entity) {
        Objects.requireNonNull(order, "Order is required");
        Objects.requireNonNull(entity, "Order entity is required");

        if (!order.getId().equals(entity.getId())) {
            throw new IllegalArgumentException(
                    "Order and entity IDs do not match"
            );
        }

        if (!order.getCustomerId().equals(entity.getCustomerId())) {
            throw new IllegalArgumentException(
                    "Cannot change the customer of an existing order"
            );
        }

        entity.updateStatus(order.getStatus());

        // Remove items that no longer exist in the domain.
        for (OrderItemEntity existing : entity.getItems()) {
            boolean stillExists = order.getItems().stream()
                    .anyMatch(item ->
                            item.productId().equals(
                                    existing.getProductId()
                            )
                    );

            if (!stillExists) {
                entity.removeItem(existing);
            }
        }

        // Update existing items or add new ones.
        for (OrderItem item : order.getItems()) {
            OrderItemEntity existing = entity.getItems()
                    .stream()
                    .filter(candidate ->
                            candidate.getProductId()
                                    .equals(item.productId())
                    )
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                existing.updateValues(
                        item.quantity(),
                        item.unitPrice()
                );
            } else {
                OrderItemEntity newItem = new OrderItemEntity(
                        UUID.randomUUID(),
                        item.productId(),
                        item.quantity(),
                        item.unitPrice()
                );

                entity.addItem(newItem);
            }
        }

        entity.updateTimestamp(
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}