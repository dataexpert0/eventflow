
package dev.eventflow.orders.application.usecase.confirm;

import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.model.OrderItem;
import dev.eventflow.orders.domain.model.OrderStatus;
import dev.eventflow.orders.domain.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfirmOrderUseCaseTest {

    private OrderRepository repository;
    private ConfirmOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(OrderRepository.class);
        useCase = new ConfirmOrderUseCase(repository);
    }

    @Test
    void shouldConfirmPendingOrder() {
        Order order = new Order(UUID.randomUUID());

        order.addItem(new OrderItem(
                UUID.randomUUID(),
                2,
                new BigDecimal("25.00")
        ));

        when(repository.findById(order.getId()))
                .thenReturn(Optional.of(order));

        when(repository.save(order))
                .thenReturn(order);

        Optional<Order> result = useCase.execute(order.getId());

        assertTrue(result.isPresent());
        assertEquals(
                OrderStatus.CONFIRMED,
                result.orElseThrow().getStatus()
        );

        verify(repository).save(order);
    }

    @Test
    void shouldReturnEmptyWhenOrderDoesNotExist() {
        UUID orderId = UUID.randomUUID();

        when(repository.findById(orderId))
                .thenReturn(Optional.empty());

        Optional<Order> result = useCase.execute(orderId);

        assertTrue(result.isEmpty());
        verify(repository, never()).save(any());
    }
}