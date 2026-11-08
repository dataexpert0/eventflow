
package dev.eventflow.orders.application.service;

import dev.eventflow.orders.application.port.EventPublisher;
import dev.eventflow.orders.domain.event.OutboxEvent;
import dev.eventflow.orders.domain.repository.OutboxEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxPublisherServiceTest {

    private OutboxEventRepository repository;
    private EventPublisher publisher;
    private OutboxPublisherService service;

    private UUID workerId;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        publisher = mock(EventPublisher.class);

        workerId = UUID.randomUUID();

        service = new OutboxPublisherService(
                repository,
                publisher,
                workerId,
                Duration.ofMinutes(5)
        );
    }

    private OutboxEvent createEvent() {
        return OutboxEvent.pending(
                UUID.randomUUID(),
                "OrderCreated",
                "{\"status\":\"PENDING\"}"
        );
    }

    @Test
    void shouldPublishAndMarkEventsInOrder() {
        OutboxEvent first = createEvent();
        OutboxEvent second = createEvent();

        when(repository.claimPending(
                eq(workerId),
                eq(10),
                any(Duration.class)
        )).thenReturn(List.of(first, second));

        when(repository.markPublished(any(), eq(workerId)))
                .thenReturn(true);

        int count = service.processBatch(10);

        assertEquals(2, count);

        InOrder inOrder = inOrder(publisher, repository);

        inOrder.verify(publisher).publish(first);
        inOrder.verify(repository)
                .markPublished(first.id(), workerId);

        inOrder.verify(publisher).publish(second);
        inOrder.verify(repository)
                .markPublished(second.id(), workerId);
    }

    @Test
    void shouldNotMarkEventWhenPublicationFails() {
        OutboxEvent event = createEvent();

        when(repository.claimPending(
                eq(workerId),
                eq(10),
                any(Duration.class)
        )).thenReturn(List.of(event));

        doThrow(new RuntimeException(
                "Broker unavailable"
        )).when(publisher).publish(event);

        assertThrows(
                RuntimeException.class,
                () -> service.processBatch(10)
        );

        verify(repository, never())
                .markPublished(any(), any());
    }

    @Test
    void shouldRejectFailedPublicationMarking() {
        OutboxEvent event = createEvent();

        when(repository.claimPending(
                eq(workerId),
                eq(10),
                any(Duration.class)
        )).thenReturn(List.of(event));

        when(repository.markPublished(
                event.id(),
                workerId
        )).thenReturn(false);

        assertThrows(
                IllegalStateException.class,
                () -> service.processBatch(10)
        );

        verify(publisher).publish(event);
        verify(repository).markPublished(
                event.id(),
                workerId
        );
    }

    @Test
    void shouldReturnZeroWhenNoEventsArePending() {
        when(repository.claimPending(
                eq(workerId),
                eq(10),
                any(Duration.class)
        )).thenReturn(List.of());

        int count = service.processBatch(10);

        assertEquals(0, count);

        verifyNoInteractions(publisher);
        verify(repository, never())
                .markPublished(any(), any());
    }
}