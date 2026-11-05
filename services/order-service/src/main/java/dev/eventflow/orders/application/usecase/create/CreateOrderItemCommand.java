package dev.eventflow.orders.application.usecase.create;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderItemCommand(UUID productId, int quantity, BigDecimal unitPrice) {

}
