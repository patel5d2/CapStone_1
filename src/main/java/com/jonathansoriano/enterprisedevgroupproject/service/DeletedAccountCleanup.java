package com.jonathansoriano.enterprisedevgroupproject.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Removes the directory profile of a Clerk account that no longer exists.
 *
 * <p>Deleting an account in Clerk used to leave its {@code student} row behind, still bound
 * to the dead subject. Nobody could reach it, and nobody could replace it either: the same
 * person signing up again with the same address was refused on every request, and could not
 * create a profile because {@code app_user} still held the address. A deleted account's
 * profile is deleted, which is also what "delete my account" means to the person asking.
 *
 * <p>Callers must hold proof of the deletion before calling this — Clerk's own answer
 * ({@code ClerkAccountLookup}) or its signed {@code user.deleted} event. It is never called
 * on an address match alone.
 *
 * <p><b>What goes and what stays.</b> The {@code student} row goes, and with it the privacy
 * choices (cascade) and the link from any uploaded photo (set null). The {@code app_user}
 * row on the same address goes, and the webhook's record of the identity. Listings, messages
 * and posts stay: they are keyed on the old subject, which no future account can present,
 * so they belong to nobody rather than to whoever takes the address next. Erasing those too
 * is Open Question 14.
 */
@Slf4j
@Service
public class DeletedAccountCleanup {

    private final NamedParameterJdbcTemplate jdbc;

    public DeletedAccountCleanup(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Deletes the profile bound to {@code deletedClerkUserId}, if there is one.
     *
     * <p>Its own transaction on purpose. The place that discovers a dead owner is identity
     * resolution, which mostly runs inside read-only transactions (reading a profile), and
     * PostgreSQL refuses a DELETE in one. Committing here also means the request that
     * discovered it carries on against the cleaned-up tables.
     *
     * @return true if a profile was removed, false if that account had none
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean retire(String deletedClerkUserId) {
        MapSqlParameterSource subject = new MapSqlParameterSource("subject", deletedClerkUserId);

        jdbc.update("DELETE FROM clerk_identity WHERE clerk_user_id = :subject", subject);

        List<Map<String, Object>> profiles = jdbc.queryForList(
                "SELECT id, email FROM student WHERE clerk_user_id = :subject", subject);
        if (profiles.isEmpty()) {
            return false;
        }
        // clerk_user_id is unique, so this is exactly one row.
        Map<String, Object> profile = profiles.get(0);

        int removed = jdbc.update("DELETE FROM student WHERE id = :id AND clerk_user_id = :subject",
                subject.addValue("id", profile.get("id")));
        // Only the row sign-up itself wrote (StudentService inserts role USER); an account
        // with any other role on this address is not this student's to take with them.
        jdbc.update("DELETE FROM app_user WHERE email = :email AND role = 'USER'",
                new MapSqlParameterSource("email", profile.get("email")));

        // Invariant 8: the subject and the row id trace this; the address is not logged.
        log.info("Clerk account {} no longer exists: removed its directory profile (student id {}).",
                deletedClerkUserId, profile.get("id"));
        return removed > 0;
    }
}
