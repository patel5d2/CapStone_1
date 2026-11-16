package com.jonathansoriano.enterprisedevgroupproject.security;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Whether an administrator has banned the account behind a token (#113).
 *
 * <p>A ban is an {@code app_user} row whose role is {@code BANNED}. It is a ban on the
 * <em>email address</em>, which is what makes it hold against the obvious ways round it:
 * <ul>
 *   <li><b>Signing up again.</b> A new Clerk account on the same address presents the same
 *       address, and is refused the same way.</li>
 *   <li><b>Deleting the account first.</b> {@code DeletedAccountCleanup} removes only the
 *       {@code USER} row sign-up wrote, so the {@code BANNED} row outlives the profile.</li>
 *   <li><b>Changing the address in Clerk.</b> The token then carries a new address, but the
 *       same subject; the profile bound to that subject still stores the banned one, and the
 *       second half of the query below finds it.</li>
 * </ul>
 * A person who registers a different address they have never used here is a new identity,
 * and nothing in this application can tell otherwise.
 *
 * <p>Asked on every request ({@link ClerkJwtAuthenticationConverter}), so a ban takes effect
 * on the banned student's very next call rather than when their session ends, and lifting
 * one works just as quickly.
 */
@Component
public class AccountBans {

    public static final String BANNED_ROLE = "BANNED";

    /** What a banned person is told, by the API and by the SPA that shows its message. */
    public static final String BANNED_MESSAGE =
            "This account has been banned from CampusBridge by an administrator. "
                    + "If you think this is a mistake, contact your CampusBridge administrator.";

    private static final String IS_BANNED = """
            SELECT count(*) FROM app_user a
            WHERE a.role = 'BANNED'
              AND (lower(a.email) = lower(:email)
                   OR a.email IN (SELECT s.email FROM student s WHERE s.clerk_user_id = :subject))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AccountBans(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** True when the token's address, or the profile bound to its subject, is banned. */
    public boolean isBanned(Jwt clerkSession) {
        if (clerkSession == null) {
            return false;
        }
        Integer matches = jdbc.queryForObject(IS_BANNED, new MapSqlParameterSource()
                .addValue("email", clerkSession.getClaimAsString("email"))
                .addValue("subject", clerkSession.getSubject()), Integer.class);
        return matches != null && matches > 0;
    }
}
