package com.jonathansoriano.enterprisedevgroupproject.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Removes everything one person posted, when an administrator bans or deletes them and asks
 * for that too (#113).
 *
 * <p>This is the opposite of the default. On its own a deleted account leaves its listings,
 * posts and comments in place ({@code DeletedAccountCleanup}); that suits someone who simply
 * left. It does not suit someone removed for what they posted, whose listings would otherwise
 * stay up with nobody answering for them.
 *
 * <p><b>What goes:</b> their listings (with photos, favourites and reports on them), posts
 * (with the comments and likes under them), comments on other people's posts, events and
 * support requests. <b>What stays:</b> groups they created, because a group holds other
 * students' posts; messages, which are half of someone else's conversation; and their likes
 * and memberships, which say nothing on their own.
 *
 * <p>Rows are matched on the Clerk subject, or on the address for rows written before
 * subjects were stored. All of it happens in one transaction: either everything listed is
 * gone or nothing is.
 */
@Slf4j
@Component
public class AccountContentRemoval {

    /** Table, subject column, address column. Fixed here; nothing from a request reaches the SQL text. */
    private record Owned(String table, String subjectColumn, String emailColumn) {
    }

    // Comments before posts only for readable counts; the foreign keys cascade either way.
    private static final List<Owned> CONTENT = List.of(
            new Owned("listing", "seller_subject", "seller_email"),
            new Owned("post_comment", "author_subject", "author_email"),
            new Owned("post", "author_subject", "author_email"),
            new Owned("event", "created_by_subject", "created_by_email"),
            new Owned("anonymous_request", "requester_subject", "requester_email"));

    private final NamedParameterJdbcTemplate jdbc;

    public AccountContentRemoval(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param subject the person's Clerk subject, or null for a profile nobody signed in to
     * @param email   the address on their profile
     * @return how many rows were removed directly (rows removed by cascade are not counted)
     */
    @Transactional
    public int removeAllBy(String subject, String email) {
        MapSqlParameterSource owner = new MapSqlParameterSource()
                .addValue("subject", subject)
                .addValue("email", email);
        int removed = 0;
        for (Owned content : CONTENT) {
            removed += jdbc.update("DELETE FROM " + content.table()
                    + " WHERE " + content.subjectColumn() + " = :subject"
                    + " OR lower(" + content.emailColumn() + ") = lower(:email)", owner);
        }
        log.info("Removed {} piece(s) of content posted by one account.", removed);
        return removed;
    }
}
