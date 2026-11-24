package com.jonathansoriano.enterprisedevgroupproject.admin;

import com.jonathansoriano.enterprisedevgroupproject.domain.UserRequest;
import com.jonathansoriano.enterprisedevgroupproject.dto.UserDto;
import com.jonathansoriano.enterprisedevgroupproject.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Creates the administrator accounts named in {@code campusbridge.admin.emails}
 * ({@code CAMPUSBRIDGE_ADMIN_EMAILS}, comma-separated) on every startup.
 *
 * <p><b>Why configuration and not a row in {@code data.sql}.</b> Sign-in belongs to Clerk, so
 * an administrator has to be a real Clerk account on a mailbox somebody controls. An address
 * hard-coded in a committed seed file would either be one nobody can sign in as, or one
 * anybody could register on a development Clerk instance. Naming the address in the
 * environment keeps the grant with whoever runs the server, and out of the repository.
 *
 * <p>An address with no {@code app_user} row gets one with the {@code ADMIN} role: a
 * dedicated administrator account, with no student profile. An address that already has a
 * row (a student who signed up earlier) is promoted in place and keeps their profile.
 * Nothing is ever demoted here; to take the role away, remove the address from the setting
 * and reset the row's role to {@code USER}. With the default in-memory database a restart
 * does the second half on its own.
 */
@Slf4j
@Component
public class AdminAccountSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final List<String> adminEmails;

    public AdminAccountSeeder(UserRepository users, @Value("${campusbridge.admin.emails:}") String adminEmails) {
        this.users = users;
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .map(AdminAccess::normalize)
                .distinct()
                .toList();
    }

    @Override
    public void run(String... args) {
        int granted = 0;
        for (int position = 0; position < adminEmails.size(); position++) {
            String email = adminEmails.get(position);
            if (!email.contains("@")) {
                // Invariant 8: an address is never logged, so only its position is.
                log.warn("Entry {} of campusbridge.admin.emails is not an email address; ignored.", position + 1);
                continue;
            }
            Optional<UserDto> existing = users.findByEmail(email);
            if (existing.isEmpty()) {
                users.insertNewUser(UserRequest.builder().role(AdminAccess.ADMIN_ROLE).email(email).build());
                granted++;
            } else if (!AdminAccess.ADMIN_ROLE.equals(existing.get().getRole())) {
                users.updateRole(existing.get().getId(), AdminAccess.ADMIN_ROLE);
                granted++;
            }
        }
        if (adminEmails.isEmpty()) {
            log.info("No administrator accounts configured (CAMPUSBRIDGE_ADMIN_EMAILS is empty); "
                    + "account management is unavailable to everyone.");
        } else {
            log.info("Administrator accounts configured: {} ({} newly granted the role).",
                    adminEmails.size(), granted);
        }
    }
}
