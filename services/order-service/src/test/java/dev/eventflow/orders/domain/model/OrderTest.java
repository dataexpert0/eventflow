
package dev.eventflow.orders.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void shouldCreateOrderWithPendingStatus()
    {
        Order order = new Order(UUID.randomUUID());

        assertNotNull(order.getId());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertTrue(order.getItems().isEmpty());
    }

    @Test
    void shouldCalculateOrderTotal()
    {
        Order order = new Order(UUID.randomUUID());

        order.addItem(new OrderItem(
                UUID.randomUUID(),
                2,
                new BigDecimal("49.90")
        ));

        order.addItem(new OrderItem(
                UUID.randomUUID(),
                1,
                new BigDecimal("20.00")
        ));

        assertEquals(
                0,
                new BigDecimal("119.80")
                        .compareTo(order.calculateTotal())
        );
    }

    @Test
    void shouldNotConfirmEmptyOrder()
    {
        Order order = new Order(UUID.randomUUID());

        assertThrows(
                IllegalStateException.class,
                order::confirm
        );
    }

    @Test
    void shouldConfirmOrderWithItems()
    {
        Order order = new Order(UUID.randomUUID());

        order.addItem(new OrderItem(
                UUID.randomUUID(),
                1,
                new BigDecimal("100.00")
        ));

        order.confirm();

        assertEquals(
                OrderStatus.CONFIRMED,
                order.getStatus()
        );
    }

    @Test
    void shouldNotModifyConfirmedOrder()
    {
        Order order = new Order(UUID.randomUUID());

        order.addItem(new OrderItem(
                UUID.randomUUID(),
                1,
                new BigDecimal("50.00")
        ));

        order.confirm();

        assertThrows(
                IllegalStateException.class,
                () -> order.addItem(new OrderItem(
                        UUID.randomUUID(),
                        1,
                        BigDecimal.TEN
                ))
        );
    }

    @Test
    void shouldRejectNegativeQuantity()
    {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrderItem(
                        UUID.randomUUID(),
                        -1,
                        BigDecimal.TEN
                )
        );
    }

    @Test
    void shouldCancelPendingOrder()
    {
        Order order = new Order(UUID.randomUUID());

        order.cancel();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void shouldNotConfirmCancelledOrder()
    {
        Order order = new Order(UUID.randomUUID());

        order.cancel();

        assertThrows(
                IllegalStateException.class,
                order::confirm
        );
    }

    @Test
    void shouldNotConfirmOrderTwice()
    {
        Order order = new Order(UUID.randomUUID());

        order.addItem(new OrderItem(
                UUID.randomUUID(),
                1,
                new BigDecimal("50.00")
        ));

        order.confirm();

        assertThrows(
                IllegalStateException.class,
                order::confirm
        );
    }

    @Test
    void shouldRejectNegativeUnitPrice()
    {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrderItem(
                        UUID.randomUUID(),
                        1,
                        new BigDecimal("-10.00")
                )
        );
    }

    @Test
    void shouldRejectZeroQuantity()
    {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrderItem(
                        UUID.randomUUID(),
                        0,
                        BigDecimal.TEN
                )
        );
    }

    @Test
    void shouldNotAllowExternalModificationOfItems()
    {
        Order order = new Order(UUID.randomUUID());

        assertThrows(
                UnsupportedOperationException.class,
                () -> order.getItems().add(
                        new OrderItem(
                                UUID.randomUUID(),
                                1,
                                BigDecimal.TEN
                        )
                )
        );
    }
}