
package dev.eventflow.orders.infrastructure.persistence.outbox;

import dev.eventflow.orders.domain.event.OutboxEvent;
import dev.eventflow.orders.domain.repository.OutboxEventRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.UUID;

import java.util.Objects;

@Repository
public class JdbcOutboxEventRepository
        implements OutboxEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxEventRepository(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate,
                "JdbcTemplate is required"
        );
    }

    @Override
    public void save(OutboxEvent event) {
        Objects.requireNonNull(event, "Event is required");

        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    id,
                    aggregate_id,
                    event_type,
                    payload,
                    occurred_at,
                    published_at
                )
                VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?)
                """,
                event.id(),
                event.aggregateId(),
                event.eventType(),
                event.payload(),
                event.occurredAt(),
                event.publishedAt()
        );
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "Limit must be greater than zero"
            );
        }

        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    aggregate_id,
                    event_type,
                    payload::text AS payload,
                    occurred_at,
                    published_at
                FROM outbox_events
                WHERE published_at IS NULL
                ORDER BY occurred_at ASC, id ASC
                LIMIT ?
                """,
                (rs, rowNum) -> new OutboxEvent(
                        rs.getObject("id", java.util.UUID.class),
                        rs.getObject(
                                "aggregate_id",
                                java.util.UUID.class
                        ),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getObject(
                                "occurred_at",
                                OffsetDateTime.class
                        ),
                        rs.getObject(
                                "published_at",
                                OffsetDateTime.class
                        )
                ),
                limit
        );
    }

    @Override
    public List<OutboxEvent> claimPending(
            UUID workerId,
            int limit,
            Duration lease
    ) {
        Objects.requireNonNull(
                workerId, "Worker ID is required"
        );
        Objects.requireNonNull(
                lease, "Lease duration is required"
        );

        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "Limit must be greater than zero"
            );
        }

        long leaseSeconds = lease.toSeconds();

        if (leaseSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Lease must be at least one second"
            );
        }

        return jdbcTemplate.query(
                """
                        WITH candidates AS (
                            SELECT id
                            FROM outbox_events
                            WHERE published_at IS NULL
                              AND (
                                  claimed_until IS NULL
                                  OR claimed_until <= CURRENT_TIMESTAMP
                              )
                            ORDER BY occurred_at ASC, id ASC
                            FOR UPDATE SKIP LOCKED
                            LIMIT ?
                        ),
                        claimed AS (
                            UPDATE outbox_events AS e
                            SET claimed_by = ?,
                                claimed_until =
                                    CURRENT_TIMESTAMP
                                    + (? * INTERVAL '1 second')
                            FROM candidates
                            WHERE e.id = candidates.id
                            RETURNING
                                e.id,
                                e.aggregate_id,
                                e.event_type,
                                e.payload::text AS payload,
                                e.occurred_at,
                                e.published_at
                        )
                        SELECT *
                        FROM claimed
                        ORDER BY occurred_at ASC, id ASC
                        """,
                (rs, rowNum) -> new OutboxEvent(
                        rs.getObject("id", UUID.class),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getObject(
                                "occurred_at",
                                OffsetDateTime.class
                        ),
                        rs.getObject(
                                "published_at",
                                OffsetDateTime.class
                        )
                ),
                limit,
                workerId,
                leaseSeconds
        );
    }

    @Override
    public boolean markPublished(
            UUID eventId,
            UUID workerId
    ) {
        Objects.requireNonNull(
                eventId,
                "Event ID is required"
        );

        Objects.requireNonNull(
                workerId,
                "Worker ID is required"
        );

        int updated = jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET published_at = CURRENT_TIMESTAMP,
                    claimed_by = NULL,
                    claimed_until = NULL
                WHERE id = ?
                  AND claimed_by = ?
                  AND claimed_until > CURRENT_TIMESTAMP
                  AND published_at IS NULL
                """,
                eventId,
                workerId
        );

        return updated == 1;
    }

}