
package dev.eventflow.notifications.application.usecase;

import dev.eventflow.notifications.domain.model.Notification;
import dev.eventflow.notifications.domain.repository.NotificationRepository;
import dev.eventflow.notifications.domain.repository.ProcessedEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class ProcessOrderEventUseCase {

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;

    public ProcessOrderEventUseCase(
            ProcessedEventRepository processedEventRepository,
            NotificationRepository notificationRepository
    ) {
        this.processedEventRepository = Objects.requireNonNull(
                processedEventRepository
        );

        this.notificationRepository = Objects.requireNonNull(
                notificationRepository
        );
    }

    @Transactional
    public boolean execute(
            UUID eventId,
            String eventType,
            UUID orderId
    ) {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(orderId, "Order ID is required");

        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException(
                    "Event type is required"
            );
        }

        String message = switch (eventType) {
            case "OrderCreated" ->
                    "Order created successfully";
            case "OrderConfirmed" ->
                    "Order confirmed successfully";
            case "OrderCancelled" ->
                    "Order cancelled successfully";
            default -> throw new IllegalArgumentException(
                    "Unsupported event type: " + eventType
            );
        };

        boolean registered =
                processedEventRepository.registerIfAbsent(
                        eventId,
                        eventType
                );

        if (!registered) {
            return false;
        }

        Notification notification = Notification.create(
                eventId,
                orderId,
                message
        );

        notificationRepository.save(notification);

        return true;
    }
}