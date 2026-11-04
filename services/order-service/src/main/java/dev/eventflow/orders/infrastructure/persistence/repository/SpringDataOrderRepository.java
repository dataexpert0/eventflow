
package dev.eventflow.orders.infrastructure.persistence.repository;

import dev.eventflow.orders.infrastructure.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataOrderRepository
        extends JpaRepository<OrderEntity, UUID> {
}
