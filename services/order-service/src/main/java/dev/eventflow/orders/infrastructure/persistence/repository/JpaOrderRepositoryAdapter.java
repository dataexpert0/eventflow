
package dev.eventflow.orders.infrastructure.persistence.repository;

import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.repository.OrderRepository;
import dev.eventflow.orders.infrastructure.persistence.mapper.OrderPersistenceMapper;
import dev.eventflow.orders.domain.exception.OrderUpdateNotSupportedException;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaOrderRepositoryAdapter implements OrderRepository {

    private final SpringDataOrderRepository repository;
    private final OrderPersistenceMapper mapper;

    public JpaOrderRepositoryAdapter(
            SpringDataOrderRepository repository
    ) {
        this.repository = repository;
        this.mapper = new OrderPersistenceMapper();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID id) {
        return repository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Order save(Order order) {
        if (repository.existsById(order.getId())) {
            throw new OrderUpdateNotSupportedException();
        }

        var entity = mapper.toEntity(order);

        return mapper.toDomain(repository.saveAndFlush(entity));
    }
}