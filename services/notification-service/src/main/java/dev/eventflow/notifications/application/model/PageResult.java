
package dev.eventflow.notifications.application.model;

import java.util.List;
import java.util.Objects;

public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        long totalPages
) {
    public PageResult {
        content = List.copyOf(
                Objects.requireNonNull(content)
        );
    }
}