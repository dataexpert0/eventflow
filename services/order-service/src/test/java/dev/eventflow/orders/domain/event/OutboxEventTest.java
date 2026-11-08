
package dev.eventflow.orders.domain.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEventTest {

    @Test
    void shouldCreatePendingOutboxEvent() {
        UUID orderId = UUID.randomUUID();

        OutboxEvent event = OutboxEvent.pending(
                orderId,
                "OrderConfirmed",
                "{\"orderId\":\"" + orderId + "\"}"
        );

        assertAll(
                () -> assertNotNull(event.id()),
                () -> assertEquals(orderId, event.aggregateId()),
                () -> assertEquals(
                        "OrderConfirmed",
                        event.eventType()
                ),
                () -> assertNotNull(event.occurredAt()),
                () -> assertNull(event.publishedAt()),
                () -> assertFalse(event.isPublished())
        );
    }

    @Test
    void shouldRejectBlankEventType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OutboxEvent.pending(
                        UUID.randomUUID(),
                        " ",
                        "{}"
                )
        );
    }

    @Test
    void shouldRejectBlankPayload() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OutboxEvent.pending(
                        UUID.randomUUID(),
                        "OrderConfirmed",
                        ""
                )
        );
    }
}