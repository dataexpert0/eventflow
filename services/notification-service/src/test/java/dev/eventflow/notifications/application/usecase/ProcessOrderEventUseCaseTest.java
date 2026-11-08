
package dev.eventflow.notifications.application.usecase;

import dev.eventflow.notifications.domain.model.Notification;
import dev.eventflow.notifications.domain.repository.NotificationRepository;
import dev.eventflow.notifications.domain.repository.ProcessedEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessOrderEventUseCaseTest {

    private ProcessedEventRepository processedRepository;
    private NotificationRepository notificationRepository;
    private ProcessOrderEventUseCase useCase;

    @BeforeEach
    void setUp() {
        processedRepository = mock(ProcessedEventRepository.class);
        notificationRepository = mock(NotificationRepository.class);

        useCase = new ProcessOrderEventUseCase(
                processedRepository,
                notificationRepository
        );
    }

    @Test
    void shouldRegisterNewEventAndCreateNotification() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        when(processedRepository.registerIfAbsent(
                eventId, "OrderCreated"
        )).thenReturn(true);

        boolean result = useCase.execute(
                eventId, "OrderCreated", orderId
        );

        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(captor.capture());

        Notification notification = captor.getValue();

        assertAll(
                () -> assertTrue(result),
                () -> assertEquals(eventId, notification.eventId()),
                () -> assertEquals(orderId, notification.orderId()),
                () -> assertEquals(
                        "Order created successfully",
                        notification.message()
                )
        );
    }

    @Test
    void shouldIgnoreDuplicateEvent() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        when(processedRepository.registerIfAbsent(
                eventId, "OrderCreated"
        )).thenReturn(false);

        boolean result = useCase.execute(
                eventId, "OrderCreated", orderId
        );

        assertFalse(result);
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void shouldRejectMissingEventId() {
        assertThrows(
                NullPointerException.class,
                () -> useCase.execute(
                        null, "OrderCreated", UUID.randomUUID()
                )
        );

        verifyNoInteractions(
                processedRepository, notificationRepository
        );
    }

    @Test
    void shouldRejectBlankEventType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(
                        UUID.randomUUID(), " ", UUID.randomUUID()
                )
        );

        verifyNoInteractions(
                processedRepository, notificationRepository
        );
    }

    @Test
    void shouldRejectUnsupportedEventType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(
                        UUID.randomUUID(),
                        "UnknownEvent",
                        UUID.randomUUID()
                )
        );

        verifyNoInteractions(
                processedRepository, notificationRepository
        );
    }

    @Test
    void shouldCreateCancellationNotification() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        when(processedRepository.registerIfAbsent(
                eventId, "OrderCancelled"
        )).thenReturn(true);

        useCase.execute(eventId, "OrderCancelled", orderId);

        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(captor.capture());

        assertEquals(
                "Order cancelled successfully",
                captor.getValue().message()
        );
    }
}
