
package dev.eventflow.notifications.application.usecase;

import dev.eventflow.notifications.application.model.PageResult;
import dev.eventflow.notifications.domain.model.NotificationSummary;
import dev.eventflow.notifications.domain.repository.NotificationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetNotificationsByOrderIdUseCaseTest {

    private NotificationRepository repository;
    private GetNotificationsByOrderIdUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        useCase = new GetNotificationsByOrderIdUseCase(
                repository
        );
    }

    @Test
    void shouldCalculateTotalPagesCorrectly() {
        UUID orderId = UUID.randomUUID();

        when(repository.findByOrderId(orderId, 20, 0L))
                .thenReturn(List.of());

        when(repository.countByOrderId(orderId))
                .thenReturn(45L);

        PageResult<NotificationSummary> result =
                useCase.execute(orderId, 0, 20);

        assertAll(
                () -> assertEquals(0, result.page()),
                () -> assertEquals(20, result.size()),
                () -> assertEquals(45, result.totalElements()),
                () -> assertEquals(3, result.totalPages())
        );

        verify(repository).findByOrderId(orderId, 20, 0L);
        verify(repository).countByOrderId(orderId);
    }

    @Test
    void shouldCalculateOffsetForSecondPage() {
        UUID orderId = UUID.randomUUID();

        when(repository.findByOrderId(orderId, 10, 10L))
                .thenReturn(List.of());

        when(repository.countByOrderId(orderId))
                .thenReturn(25L);

        PageResult<NotificationSummary> result =
                useCase.execute(orderId, 1, 10);

        assertEquals(1, result.page());
        assertEquals(3, result.totalPages());

        verify(repository).findByOrderId(orderId, 10, 10L);
    }

    @Test
    void shouldReturnZeroPagesWhenNoResultsExist() {
        UUID orderId = UUID.randomUUID();

        when(repository.findByOrderId(orderId, 20, 0L))
                .thenReturn(List.of());

        when(repository.countByOrderId(orderId))
                .thenReturn(0L);

        PageResult<NotificationSummary> result =
                useCase.execute(orderId, 0, 20);

        assertTrue(result.content().isEmpty());
        assertEquals(0, result.totalElements());
        assertEquals(0, result.totalPages());
    }

    @Test
    void shouldRejectInvalidPaginationParameters() {
        UUID orderId = UUID.randomUUID();

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> useCase.execute(orderId, -1, 20)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> useCase.execute(orderId, 0, 0)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> useCase.execute(orderId, 0, 101)
                )
        );

        verifyNoInteractions(repository);
    }

    @Test
    void shouldCalculateLargeOffsetWithoutIntegerOverflow() {
        UUID orderId = UUID.randomUUID();

        long expectedOffset =
                (long) Integer.MAX_VALUE * 100;

        when(repository.findByOrderId(
                orderId, 100, expectedOffset
        )).thenReturn(List.of());

        when(repository.countByOrderId(orderId))
                .thenReturn(0L);

        useCase.execute(
                orderId,
                Integer.MAX_VALUE,
                100
        );

        verify(repository).findByOrderId(
                orderId, 100, expectedOffset
        );
    }
}