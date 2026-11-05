
package dev.eventflow.orders.application.usecase.get;

import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.repository.OrderRepository;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetOrderUseCaseTest {

    private final OrderRepository repository =
            mock(OrderRepository.class);

    private final GetOrderUseCase useCase =
            new GetOrderUseCase(repository);

    @Test
    void shouldReturnOrderWhenItExists() {
        Order order = new Order(UUID.randomUUID());

        when(repository.findById(order.getId()))
                .thenReturn(Optional.of(order));

        Optional<Order> result =
                useCase.execute(order.getId());

        assertTrue(result.isPresent());
        assertEquals(order.getId(), result.get().getId());

        verify(repository).findById(order.getId());
    }

    @Test
    void shouldReturnEmptyWhenOrderDoesNotExist() {
        UUID orderId = UUID.randomUUID();

        when(repository.findById(orderId))
                .thenReturn(Optional.empty());

        Optional<Order> result = useCase.execute(orderId);

        assertTrue(result.isEmpty());

        verify(repository).findById(orderId);
    }

    @Test
    void shouldRejectNullOrderId() {
        assertThrows(
                NullPointerException.class,
                () -> useCase.execute(null)
        );

        verifyNoInteractions(repository);
    }
}