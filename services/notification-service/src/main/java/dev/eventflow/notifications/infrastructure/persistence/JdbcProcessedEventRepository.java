
package dev.eventflow.notifications.infrastructure.persistence;

import dev.eventflow.notifications.domain.repository.ProcessedEventRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.UUID;

@Repository
public class JdbcProcessedEventRepository
        implements ProcessedEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcProcessedEventRepository(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate,
                "JdbcTemplate is required"
        );
    }

    @Override
    public boolean registerIfAbsent(
            UUID eventId,
            String eventType
    ) {
        Objects.requireNonNull(
                eventId,
                "Event ID is required"
        );

        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException(
                    "Event type is required"
            );
        }

        int inserted = jdbcTemplate.update(
                """
                INSERT INTO processed_events (
                    event_id,
                    event_type
                )
                VALUES (?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                eventId,
                eventType
        );

        return inserted == 1;
    }
}