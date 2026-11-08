
package dev.eventflow.notifications.infrastructure.messaging;

import dev.eventflow.notifications.application.usecase.ProcessOrderEventUseCase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderEventListenerTest {

    private ProcessOrderEventUseCase useCase;
    private OrderEventListener listener;

    @BeforeEach
    void setUp() {
        useCase = mock(ProcessOrderEventUseCase.class);
        listener = new OrderEventListener(
                useCase,
                JsonMapper.builder().build()
        );
    }

    private Message createMessage(
            UUID eventId,
            String eventType
    ) {
        MessageProperties properties =
                new MessageProperties();

        properties.setMessageId(eventId.toString());
        properties.setHeader("eventType", eventType);

        String payload = """
        {
            "orderId": "%s",
            "status": "PENDING"
        }
        """.formatted(UUID.randomUUID());

        return new Message(
                payload.getBytes(StandardCharsets.UTF_8),
                properties
        );
    }

    @Test
    void shouldProcessNewEvent() {
        UUID eventId = UUID.randomUUID();

        when(useCase.execute(
                eq(eventId),
                eq("OrderCreated"),
                any(UUID.class)
        )).thenReturn(true);

        listener.receive(
                createMessage(eventId, "OrderCreated")
        );

        verify(useCase).execute(
                eq(eventId),
                eq("OrderCreated"),
                any(UUID.class)
        );
    }

    @Test
    void shouldIgnoreDuplicateEvent() {
        UUID eventId = UUID.randomUUID();

        when(useCase.execute(
                eq(eventId),
                eq("OrderCreated"),
                any(UUID.class)
        )).thenReturn(false);

        listener.receive(
                createMessage(eventId, "OrderCreated")
        );

        verify(useCase).execute(
                eq(eventId),
                eq("OrderCreated"),
                any(UUID.class)
        );
    }

    @Test
    void shouldRejectMissingEventId() {
        MessageProperties properties =
                new MessageProperties();

        properties.setHeader(
                "eventType",
                "OrderCreated"
        );

        Message message = new Message(
                new byte[0],
                properties
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> listener.receive(message)
        );

        verifyNoInteractions(useCase);
    }

    @Test
    void shouldRejectInvalidEventId() {
        MessageProperties properties =
                new MessageProperties();

        properties.setMessageId("not-a-uuid");
        properties.setHeader(
                "eventType",
                "OrderCreated"
        );

        Message message = new Message(
                new byte[0],
                properties
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> listener.receive(message)
        );

        verifyNoInteractions(useCase);
    }
}
