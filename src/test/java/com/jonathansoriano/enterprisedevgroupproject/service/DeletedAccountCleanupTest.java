package com.jonathansoriano.enterprisedevgroupproject.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a deleted Clerk account takes with it, and what it must not.
 *
 * <p>Not {@code @Transactional}, unlike its neighbours: {@code retire} commits in a
 * transaction of its own, so it cannot see rows a test transaction has not committed. The
 * rows are committed here and removed again afterwards.
 */
@SpringBootTest
class DeletedAccountCleanupTest {

    private static final String DELETED = "user_cleanupDeleted";
    private static final String BYSTANDER = "user_cleanupBystander";
    private static final String DELETED_EMAIL = "cleanup.deleted@mail.uc.edu";
    private static final String BYSTANDER_EMAIL = "cleanup.bystander@mail.uc.edu";

    @Autowired
    DeletedAccountCleanup cleanup;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        clear();
        long deleted = insertStudent(DELETED, DELETED_EMAIL);
        insertStudent(BYSTANDER, BYSTANDER_EMAIL);
        jdbc.update("INSERT INTO profile_privacy (student_id, show_major) VALUES (?, true)", deleted);
        jdbc.update("INSERT INTO image_asset (id, owner_subject, public_id, student_id) VALUES ('cleanup-asset', ?, 'cleanup/photo', ?)",
                DELETED, deleted);
        for (String subject : new String[]{DELETED, BYSTANDER}) {
            jdbc.update("INSERT INTO clerk_identity (clerk_user_id, email, last_event_id, last_event_at) "
                    + "VALUES (?, ?, 'msg_cleanup', CURRENT_TIMESTAMP)", subject, subject + "@mail.uc.edu");
        }
    }

    @AfterEach
    void clear() {
        jdbc.update("DELETE FROM image_asset WHERE id = 'cleanup-asset'");
        jdbc.update("DELETE FROM clerk_identity WHERE clerk_user_id IN (?, ?)", DELETED, BYSTANDER);
        jdbc.update("DELETE FROM student WHERE email IN (?, ?)", DELETED_EMAIL, BYSTANDER_EMAIL);
        jdbc.update("DELETE FROM app_user WHERE email IN (?, ?)", DELETED_EMAIL, BYSTANDER_EMAIL);
    }

    private long insertStudent(String subject, String email) {
        jdbc.update("INSERT INTO app_user (role, email) VALUES ('USER', ?)", email);
        jdbc.update("INSERT INTO student (first_name, last_name, resident_city, resident_state, university_id, grade, "
                + "major, email, clerk_user_id) VALUES ('Cleanup', 'Test', 'Cincinnati', 'OH', "
                + "(SELECT min(id) FROM university), 'Senior', 'History', ?, ?)", email, subject);
        return jdbc.queryForObject("SELECT id FROM student WHERE email = ?", Long.class, email);
    }

    private int count(String table, String column, String value) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
    }

    @Test
    void theDeletedAccountsProfileIsRemoved_soTheAddressCanSignUpAgain() {
        assertTrue(cleanup.retire(DELETED));

        assertEquals(0, count("student", "clerk_user_id", DELETED));
        // insertNewStudent refuses an address app_user still holds, so this must go too.
        assertEquals(0, count("app_user", "email", DELETED_EMAIL));
        assertEquals(0, count("clerk_identity", "clerk_user_id", DELETED));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM profile_privacy p WHERE NOT EXISTS "
                + "(SELECT 1 FROM student s WHERE s.id = p.student_id)", Integer.class), "privacy choices go with the profile");
        // The photo record survives for the image cleanup job, no longer pointing at anyone.
        assertNull(jdbc.queryForObject("SELECT student_id FROM image_asset WHERE id = 'cleanup-asset'", Long.class));
    }

    @Test
    void nobodyElseIsTouched() {
        cleanup.retire(DELETED);

        assertEquals(1, count("student", "clerk_user_id", BYSTANDER));
        assertEquals(1, count("app_user", "email", BYSTANDER_EMAIL));
        assertEquals(1, count("clerk_identity", "clerk_user_id", BYSTANDER));
    }

    @Test
    void anAccountWithNoProfile_isNothingToRemove() {
        assertFalse(cleanup.retire("user_neverHadAProfile"));
        // ...and saying so twice is as harmless as once: Clerk retries its deliveries.
        assertTrue(cleanup.retire(DELETED));
        assertFalse(cleanup.retire(DELETED));
    }

    @Test
    void anotherRoleOnTheSameAddress_isNotThisStudentsToTake() {
        jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE email = ?", DELETED_EMAIL);

        cleanup.retire(DELETED);

        assertEquals(1, count("app_user", "email", DELETED_EMAIL));
    }
}
