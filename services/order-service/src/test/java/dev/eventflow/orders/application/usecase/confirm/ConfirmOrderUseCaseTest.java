
package dev.eventflow.orders.application.usecase.confirm;

import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.model.OrderItem;
import dev.eventflow.orders.domain.model.OrderStatus;
import dev.eventflow.orders.domain.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.eventflow.orders.domain.repository.OutboxEventRepository;
import dev.eventflow.orders.domain.event.OutboxEvent;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfirmOrderUseCaseTest {

    private OutboxEventRepository outboxRepository;
    private OrderRepository repository;
    private ConfirmOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(OrderRepository.class);
        outboxRepository = mock(OutboxEventRepository.class);

        useCase = new ConfirmOrderUseCase(
                repository,
                outboxRepository
        );
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
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void shouldRegisterOutboxEventWhenConfirmingOrder() {
        Order order = new Order(UUID.randomUUID());

        order.addItem(new OrderItem(
                UUID.randomUUID(),
                1,
                new BigDecimal("50.00")
        ));

        when(repository.findById(order.getId()))
                .thenReturn(Optional.of(order));

        when(repository.save(order))
                .thenReturn(order);

        useCase.execute(order.getId());

        ArgumentCaptor<OutboxEvent> captor =
                ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxRepository).save(captor.capture());

        OutboxEvent event = captor.getValue();

        assertAll(
                () -> assertEquals(
                        order.getId(),
                        event.aggregateId()
                ),
                () -> assertEquals(
                        "OrderConfirmed",
                        event.eventType()
                ),
                () -> assertTrue(
                        event.payload().contains(
                                order.getId().toString()
                        )
                ),
                () -> assertFalse(event.isPublished()),
                () -> assertEquals(
                        OrderStatus.CONFIRMED,
                        order.getStatus()
                )
        );
    }
}