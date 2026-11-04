
package dev.eventflow.orders.domain.repository;

import dev.eventflow.orders.domain.model.Order;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(UUID id);
}
