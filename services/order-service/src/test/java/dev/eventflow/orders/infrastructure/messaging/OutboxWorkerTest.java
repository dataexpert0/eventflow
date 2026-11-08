
package dev.eventflow.orders.infrastructure.messaging;

import dev.eventflow.orders.application.service.OutboxPublisherService;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class OutboxWorkerTest {

    @Test
    void shouldProcessConfiguredBatchSize() {
        OutboxPublisherService service =
                mock(OutboxPublisherService.class);

        OutboxWorker worker =
                new OutboxWorker(service, 10);

        worker.processPendingEvents();

        verify(service).processBatch(10);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldHandlePublisherFailureWithoutCrashing() {
        OutboxPublisherService service =
                mock(OutboxPublisherService.class);

        when(service.processBatch(10))
                .thenThrow(new IllegalStateException(
                        "Broker unavailable"
                ));

        OutboxWorker worker =
                new OutboxWorker(service, 10);

        assertDoesNotThrow(worker::processPendingEvents);

        verify(service).processBatch(10);
    }
}
