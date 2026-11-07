
package dev.eventflow.orders.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItemEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(
            name = "unit_price",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal unitPrice;

    protected OrderItemEntity() {
    }

    public OrderItemEntity(
            UUID id,
            UUID productId,
            int quantity,
            BigDecimal unitPrice
    ) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public void updateValues(
            int quantity,
            BigDecimal unitPrice
    ) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be positive"
            );
        }

        Objects.requireNonNull(
                unitPrice, "Unit price is required"
        );

        if (unitPrice.signum() < 0
                || unitPrice.scale() > 2) {
            throw new IllegalArgumentException(
                    "Invalid unit price"
            );
        }

        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    void setOrder(OrderEntity order) {
        this.order = order;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}