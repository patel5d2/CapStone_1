package com.jonathansoriano.enterprisedevgroupproject.webhook;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * The verified Clerk identities this application has been told about.
 *
 * <p>Idempotency and ordering are the same problem: Clerk retries a delivery until it
 * gets a 2xx and does not guarantee order, so a row advances only when the incoming event
 * is newer than the one already recorded. A retry carries the same timestamp and changes
 * nothing; a late-arriving older event cannot roll an address back.
 *
 * <p>Plain UPDATE-then-INSERT rather than an upsert statement: H2 has no equivalent of
 * PostgreSQL's conditional {@code ON CONFLICT ... DO UPDATE ... WHERE}.
 */
@Repository
public class ClerkIdentityRepository {

    private static final String ADVANCE_IDENTITY = """
            UPDATE clerk_identity
            SET email = :email,
                last_event_id = :lastEventId,
                last_event_at = :lastEventAt,
                updated_at = CURRENT_TIMESTAMP
            WHERE clerk_user_id = :clerkUserId AND last_event_at < :lastEventAt
            """;

    private static final String INSERT_IDENTITY = """
            INSERT INTO clerk_identity (clerk_user_id, email, last_event_id, last_event_at)
            SELECT :clerkUserId, :email, :lastEventId, :lastEventAt
            WHERE NOT EXISTS (SELECT 1 FROM clerk_identity WHERE clerk_user_id = :clerkUserId)
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

        int advanced = jdbcTemplate.update(ADVANCE_IDENTITY, params);
        if (advanced > 0) {
            return advanced;
        }
        try {
            return jdbcTemplate.update(INSERT_IDENTITY, params);
        } catch (DuplicateKeyException raced) {
            // A concurrent first delivery inserted between the two statements; the
            // UPDATE's newer-than check decides again against what it wrote.
            return jdbcTemplate.update(ADVANCE_IDENTITY, params);
        }
    }

    /**
     * Clerk subjects whose most recent recorded address is {@code email}. More than one
     * means the address was recycled between accounts, which callers must treat as unknown.
     */
    public List<String> subjectsForEmail(String email) {
        return jdbcTemplate.queryForList(
                "SELECT clerk_user_id FROM clerk_identity WHERE lower(email) = lower(:email)",
                new MapSqlParameterSource("email", email), String.class);
    }
}
