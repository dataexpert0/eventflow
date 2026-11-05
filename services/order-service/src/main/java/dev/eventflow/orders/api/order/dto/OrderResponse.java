
package dev.eventflow.orders.api.order.dto;

import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        List<OrderItemResponse> items,
        BigDecimal total
) {

    public static OrderResponse fromDomain(Order order) {
        List<OrderItemResponse> items = order.getItems()
                .stream()
                .map(item -> new OrderItemResponse(
                        item.productId(),
                        item.quantity(),
                        item.unitPrice()
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                items,
                order.calculateTotal()
        );
    }
}