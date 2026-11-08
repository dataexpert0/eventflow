
package dev.eventflow.notifications.infrastructure.persistence;

import dev.eventflow.notifications.domain.model.Notification;
import dev.eventflow.notifications.domain.repository.NotificationRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.eventflow.notifications.domain.model.NotificationSummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import java.util.Objects;

@Repository
public class JdbcNotificationRepository
        implements NotificationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcNotificationRepository(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate,
                "JdbcTemplate is required"
        );
    }

    @Override
    public void save(Notification notification) {
        Objects.requireNonNull(
                notification,
                "Notification is required"
        );

        jdbcTemplate.update(
                """
                INSERT INTO notifications (
                    id,
                    event_id,
                    order_id,
                    message
                )
                VALUES (?, ?, ?, ?)
                """,
                notification.id(),
                notification.eventId(),
                notification.orderId(),
                notification.message()
        );
    }

    @Override
    public List<NotificationSummary> findByOrderId(
            UUID orderId,
            int limit,
            long offset
    ) {
        Objects.requireNonNull(orderId);

        if (limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException(
                    "Invalid pagination parameters"
            );
        }

        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    event_id,
                    order_id,
                    message,
                    created_at
                FROM notifications
                WHERE order_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> new NotificationSummary(
                        rs.getObject("id", UUID.class),
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("order_id", UUID.class),
                        rs.getString("message"),
                        rs.getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                ),
                orderId,
                limit,
                offset
        );
    }

    @Override
    public long countByOrderId(UUID orderId) {
        Objects.requireNonNull(orderId);

        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM notifications
                WHERE order_id = ?
                """,
                Long.class,
                orderId
        );

        return Objects.requireNonNull(count);
    }
}
