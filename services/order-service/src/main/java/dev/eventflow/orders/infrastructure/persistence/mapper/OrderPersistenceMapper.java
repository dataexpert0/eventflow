
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

        return Order.restore(
                entity.getId(),
                entity.getCustomerId(),
                entity.getStatus(),
                items
        );
    }
}