package dev.eventflow.orders.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record OrderItem(UUID productId, int quantity, BigDecimal unitPrice) {
    public OrderItem {
        Objects.requireNonNull(productId, "Product ID is required");

        Objects.requireNonNull(unitPrice, "Unit price is required");

        if (quantity <= 0)
        {
            throw new IllegalArgumentException("Quantity must be greater than zero!");
        }

        if (unitPrice.signum() < 0)
        {
            throw new IllegalArgumentException("Unit price cannot be negative!");
        }

        if (unitPrice.scale() > 2)
        {
            throw new IllegalArgumentException(
                    "Unit price cannot have more than two decimal places."
            );
        }
    }
    public BigDecimal subtotal()
    {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}