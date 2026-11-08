
package dev.eventflow.notifications.application.usecase;

import dev.eventflow.notifications.application.model.PageResult;
import dev.eventflow.notifications.domain.model.NotificationSummary;
import dev.eventflow.notifications.domain.repository.NotificationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GetNotificationsByOrderIdUseCase {

    private final NotificationRepository repository;

    public GetNotificationsByOrderIdUseCase(
            NotificationRepository repository
    ) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Transactional(readOnly = true)
    public PageResult<NotificationSummary> execute(
            UUID orderId,
            int page,
            int size
    ) {
        Objects.requireNonNull(orderId);

        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Invalid pagination parameters"
            );
        }

        long offset = (long) page * size;

        List<NotificationSummary> content =
                repository.findByOrderId(
                        orderId,
                        size,
                        offset
                );

        long totalElements =
                repository.countByOrderId(orderId);

        long totalPages =
                (totalElements / size)
                        + (totalElements % size == 0 ? 0 : 1);

        return new PageResult<>(
                content,
                page,
                size,
                totalElements,
                totalPages
        );
    }
}