package com.jonathansoriano.enterprisedevgroupproject.webhook;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * The verified Clerk identities this application has been told about.
 *
 * <p>One statement, because idempotency and ordering are the same problem: Clerk retries
 * a delivery until it gets a 2xx and does not guarantee order, so the upsert advances a
 * row only when the incoming event is newer than the one already recorded. A retry
 * carries the same timestamp and changes nothing; a late-arriving older event cannot roll
 * an address back.
 */
@Repository
public class ClerkIdentityRepository {

    private static final String UPSERT_IDENTITY = """
            INSERT INTO clerk_identity (clerk_user_id, email, last_event_id, last_event_at)
            VALUES (:clerkUserId, :email, :lastEventId, :lastEventAt)
            ON CONFLICT (clerk_user_id) DO UPDATE
            SET email = EXCLUDED.email,
                last_event_id = EXCLUDED.last_event_id,
                last_event_at = EXCLUDED.last_event_at,
                updated_at = now()
            WHERE clerk_identity.last_event_at < EXCLUDED.last_event_at
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ClerkIdentityRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Records an identity, or refreshes it when this event is newer than the last one
     * applied.
     *
     * @return the number of rows written: 1 for a first delivery or a genuine update,
     *         0 when the event was a retry or arrived out of order. 0 is a success, not
     *         a failure — it means the database already reflects this event or a later one.
     */
    public int record(String clerkUserId, String email, String eventId, Instant eventAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("clerkUserId", clerkUserId)
                .addValue("email", email)
                .addValue("lastEventId", eventId)
                .addValue("lastEventAt", OffsetDateTime.ofInstant(eventAt, ZoneOffset.UTC));

        return jdbcTemplate.update(UPSERT_IDENTITY, params);
    }
}
