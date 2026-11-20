package com.jonathansoriano.enterprisedevgroupproject.admin;

import com.jonathansoriano.enterprisedevgroupproject.dto.UserDto;
import com.jonathansoriano.enterprisedevgroupproject.repository.UserRepository;
import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * The administrator capability (#113): who may manage other students' accounts.
 *
 * <p>An administrator is an {@code app_user} row whose {@code role} is {@code ADMIN}, matched
 * on the verified address in the caller's Clerk token. That column is the one place the
 * capability lives, and it is deliberately out of every student's reach: sign-up only ever
 * writes {@code USER} ({@code StudentService.insertNewStudent}), no endpoint updates it, and
 * the profile a student can edit is a different table. Changing a request body, a query
 * parameter or one's own profile therefore cannot grant it. Rows become {@code ADMIN} only
 * through {@link AdminAccountSeeder}, from server configuration.
 *
 * <p>It is asked on every administrator action rather than once per session, so a role that
 * is taken away stops working on the next request. Every method in
 * {@link AdminAccountService} starts with {@link #require}: the check is a capability
 * asserted in the service, never something inferred from the {@code /api/admin} prefix.
 */
@Service
public class AdminAccess {

    public static final String ADMIN_ROLE = "ADMIN";

    /** Deliberately says nothing about what the caller tried to reach. */
    static final String NOT_AN_ADMIN_MESSAGE = "Administrator access is required.";

    private final UserRepository users;

    public AdminAccess(UserRepository users) {
        this.users = users;
    }

    /** True when the caller's verified address holds the administrator role. */
    public boolean isAdmin(Jwt clerkSession) {
        return holdsAdminRole(CurrentUser.emailOf(clerkSession));
    }

    /** True when {@code email} holds the administrator role; false for null or unknown. */
    public boolean holdsAdminRole(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return users.findByEmail(normalize(email))
                .map(UserDto::getRole)
                .filter(ADMIN_ROLE::equals)
                .isPresent();
    }

    /**
     * Refuses anyone who is not an administrator.
     *
     * @throws ResponseStatusException 403 for a signed-in caller without the role, 401 when
     *         the token carries no address to check
     */
    public void require(Jwt clerkSession) {
        if (!isAdmin(clerkSession)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, NOT_AN_ADMIN_MESSAGE);
        }
    }

    /** Clerk stores addresses lower-cased, and the seeder writes them the same way. */
    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
