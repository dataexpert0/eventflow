
package dev.eventflow.orders.application.usecase.create;

import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.model.OrderStatus;
import dev.eventflow.orders.domain.repository.OrderRepository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreateOrderUseCaseTest {

    private final OrderRepository repository =
            mock(OrderRepository.class);

    private final CreateOrderUseCase useCase =
            new CreateOrderUseCase(repository);

    @Test
    void shouldCreateOrderWithItems() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        CreateOrderCommand command = new CreateOrderCommand(
                customerId,
                List.of(new CreateOrderItemCommand(
                        productId,
                        2,
                        new BigDecimal("49.90")
                ))
        );

        when(repository.save(any(Order.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        Order result = useCase.execute(command);

        assertAll(
                () -> assertEquals(
                        customerId, result.getCustomerId()
                ),
                () -> assertEquals(
                        OrderStatus.PENDING, result.getStatus()
                ),
                () -> assertEquals(1, result.getItems().size()),
                () -> assertEquals(
                        0,
                        new BigDecimal("99.80")
                                .compareTo(result.calculateTotal())
                )
        );

        verify(repository, times(1)).save(any(Order.class));
    }

    @Test
    void shouldRejectOrderWithoutItems() {
        CreateOrderCommand command = new CreateOrderCommand(
                UUID.randomUUID(),
                List.of()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(command)
        );

        verifyNoInteractions(repository);
    }
}
