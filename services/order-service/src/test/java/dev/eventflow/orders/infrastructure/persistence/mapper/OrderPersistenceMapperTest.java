
package dev.eventflow.orders.infrastructure.persistence.mapper;

import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.model.OrderItem;
import dev.eventflow.orders.domain.model.OrderStatus;
import dev.eventflow.orders.infrastructure.persistence.entity.OrderEntity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderPersistenceMapperTest {

    private final OrderPersistenceMapper mapper =
            new OrderPersistenceMapper();

    @Test
    void shouldConvertDomainOrderToJpaEntity() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Order order = new Order(customerId);

        order.addItem(new OrderItem(
                productId,
                2,
                new BigDecimal("49.90")
        ));

        OrderEntity entity = mapper.toEntity(order);

        assertEquals(order.getId(), entity.getId());
        assertEquals(customerId, entity.getCustomerId());
        assertEquals(OrderStatus.PENDING, entity.getStatus());

        assertEquals(1, entity.getItems().size());

        assertEquals(
                productId,
                entity.getItems().getFirst().getProductId()
        );

        assertNotNull(entity.getItems().getFirst().getId());
    }

    @Test
    void shouldRestoreOrderFromJpaEntity() {
        UUID customerId = UUID.randomUUID();

        Order original = new Order(customerId);

        original.addItem(new OrderItem(
                UUID.randomUUID(),
                3,
                new BigDecimal("25.00")
        ));

        original.confirm();

        OrderEntity entity = mapper.toEntity(original);
        Order restored = mapper.toDomain(entity);

        assertEquals(original.getId(), restored.getId());
        assertEquals(customerId, restored.getCustomerId());
        assertEquals(OrderStatus.CONFIRMED, restored.getStatus());

        assertEquals(1, restored.getItems().size());

        assertEquals(
                0,
                original.calculateTotal()
                        .compareTo(restored.calculateTotal())
        );
    }
}