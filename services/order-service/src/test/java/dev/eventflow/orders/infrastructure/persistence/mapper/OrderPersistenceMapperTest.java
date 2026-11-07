
package dev.eventflow.orders.infrastructure.persistence.mapper;

import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.model.OrderItem;
import dev.eventflow.orders.domain.model.OrderStatus;
import dev.eventflow.orders.infrastructure.persistence.entity.OrderEntity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
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

    @Test
    void shouldUpdateEntityWithoutChangingExistingItemId() {
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Order order = new Order(customerId);

        order.addItem(new OrderItem(
                productId,
                2,
                new BigDecimal("25.00")
        ));

        OrderEntity entity = mapper.toEntity(order);

        UUID originalItemId =
                entity.getItems().getFirst().getId();

        var originalCreatedAt = entity.getCreatedAt();

        Order updatedOrder = Order.restore(
                order.getId(),
                customerId,
                OrderStatus.PENDING,
                List.of(new OrderItem(
                        productId,
                        5,
                        new BigDecimal("25.00")
                ))
        );

        mapper.updateEntity(updatedOrder, entity);

        assertEquals(
                originalItemId,
                entity.getItems().getFirst().getId()
        );

        assertEquals(
                5,
                entity.getItems().getFirst().getQuantity()
        );

        assertEquals(originalCreatedAt, entity.getCreatedAt());
    }

    @Test
    void shouldRejectChangingCustomerOfExistingOrder() {
        UUID originalCustomerId = UUID.randomUUID();
        UUID anotherCustomerId = UUID.randomUUID();

        Order original = new Order(originalCustomerId);
        OrderEntity entity = mapper.toEntity(original);

        Order changed = Order.restore(
                original.getId(),
                anotherCustomerId,
                OrderStatus.PENDING,
                List.of()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mapper.updateEntity(changed, entity)
        );

        assertEquals(
                "Cannot change the customer of an existing order",
                exception.getMessage()
        );

        assertEquals(
                originalCustomerId,
                entity.getCustomerId()
        );
    }


    @Test
    void shouldRestorePersistenceVersionFromEntity() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Order original = Order.restore(
                orderId,
                customerId,
                OrderStatus.PENDING,
                List.of(),
                5L
        );

        OrderEntity entity = mapper.toEntity(original);

        // A new JPA entity has no persistence version yet.
        assertNull(entity.getVersion());
    }
}