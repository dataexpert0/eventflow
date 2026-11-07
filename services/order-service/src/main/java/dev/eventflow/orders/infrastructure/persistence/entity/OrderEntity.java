
package dev.eventflow.orders.infrastructure.persistence.entity;

import dev.eventflow.orders.domain.model.OrderStatus;
import jakarta.persistence.*;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Long getVersion() {
        return version;
    }

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {
    }

    public OrderEntity(
            UUID id,
            UUID customerId,
            OrderStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void addItem(OrderItemEntity item) {
        items.add(item);
        item.setOrder(this);
    }

    public void updateStatus(OrderStatus status) {
        this.status = Objects.requireNonNull(
                status, "Order status is required"
        );
    }

    public void updateTimestamp(OffsetDateTime updatedAt) {
        this.updatedAt = Objects.requireNonNull(
                updatedAt, "Updated at is required"
        );
    }

    public void removeItem(OrderItemEntity item) {
        this.items.remove(item);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderItemEntity> getItems() {
        return List.copyOf(items);
    }
}