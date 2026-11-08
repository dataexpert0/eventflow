
package dev.eventflow.notifications.api;

import dev.eventflow.notifications.application.model.PageResult;
import dev.eventflow.notifications.application.usecase.GetNotificationsByOrderIdUseCase;
import dev.eventflow.notifications.domain.model.NotificationSummary;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final GetNotificationsByOrderIdUseCase useCase;

    public NotificationController(
            GetNotificationsByOrderIdUseCase useCase
    ) {
        this.useCase = Objects.requireNonNull(useCase);
    }

    @GetMapping
    public PageResult<NotificationSummary> findByOrderId(
            @RequestParam UUID orderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid pagination parameters"
            );
        }

        return useCase.execute(orderId, page, size);
    }
}
