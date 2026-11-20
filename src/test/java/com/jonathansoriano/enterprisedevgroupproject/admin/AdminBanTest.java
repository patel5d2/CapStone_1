package com.jonathansoriano.enterprisedevgroupproject.admin;

import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminBanResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminStudentResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminUserReportResponse;
import com.jonathansoriano.enterprisedevgroupproject.domain.StudentRequest;
import com.jonathansoriano.enterprisedevgroupproject.exception.SearchNotFoundException;
import com.jonathansoriano.enterprisedevgroupproject.repository.StudentRepository;
import com.jonathansoriano.enterprisedevgroupproject.security.AccountBans;
import com.jonathansoriano.enterprisedevgroupproject.security.ClerkAccountDeletion;
import com.jonathansoriano.enterprisedevgroupproject.security.ClerkAccountDeletion.Outcome;
import com.jonathansoriano.enterprisedevgroupproject.security.ClerkJwtAuthenticationConverter;
import com.jonathansoriano.enterprisedevgroupproject.service.DeletedAccountCleanup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Banning (#113), against the real schema: what a ban stops, the ways round it that it also
 * stops, and that lifting one really gives the account back.
 *
 * <p>Not {@code @Transactional}, for the same reason as {@code AdminAccountServiceTest}:
 * deleting an account commits in its own transaction.
 */
@SpringBootTest
class AdminBanTest {

    private static final String ADMIN_SUBJECT = "user_banTestAdmin";
    private static final String ADMIN_EMAIL = "bantest.admin@mail.uc.edu";
    private static final String STUDENT_SUBJECT = "user_banTestStudent";
    private static final String STUDENT_EMAIL = "bantest.student@mail.uc.edu";
    private static final String BYSTANDER_SUBJECT = "user_banTestBystander";
    private static final String BYSTANDER_EMAIL = "bantest.bystander@mail.uc.edu";
    private static final String[] EMAILS = {ADMIN_EMAIL, STUDENT_EMAIL, BYSTANDER_EMAIL, "bantest.new@mail.uc.edu"};

    @Autowired
    AdminAccountService service;
    @Autowired
    AccountBans bans;
    @Autowired
    ClerkJwtAuthenticationConverter converter;
    @Autowired
    StudentRepository directory;
    @Autowired
    DeletedAccountCleanup cleanup;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    ClerkAccountDeletion clerk;

    private long studentId;

    @BeforeEach
    void seed() {
        clear();
        jdbc.update("INSERT INTO app_user (role, email) VALUES ('ADMIN', ?)", ADMIN_EMAIL);
        studentId = insertStudent("Banned", STUDENT_EMAIL, STUDENT_SUBJECT);
        insertStudent("Bystander", BYSTANDER_EMAIL, BYSTANDER_SUBJECT);
        when(clerk.delete(any(), any())).thenReturn(Outcome.REMOVED);
    }

    @AfterEach
    void clear() {
        for (String email : EMAILS) {
            jdbc.update("DELETE FROM listing WHERE seller_email = ?", email);
            jdbc.update("DELETE FROM post_comment WHERE author_email = ?", email);
            jdbc.update("DELETE FROM post WHERE author_email = ?", email);
            jdbc.update("DELETE FROM event WHERE created_by_email = ?", email);
            jdbc.update("DELETE FROM anonymous_request WHERE requester_email = ?", email);
            jdbc.update("DELETE FROM user_report WHERE reporter_email = ?", email);
            jdbc.update("DELETE FROM student WHERE email = ?", email);
            jdbc.update("DELETE FROM app_user WHERE email = ?", email);
        }
    }

    private long insertStudent(String firstName, String email, String subject) {
        jdbc.update("INSERT INTO app_user (role, email) VALUES ('USER', ?)", email);
        jdbc.update("INSERT INTO student (first_name, last_name, resident_city, resident_state, university_id, "
                + "grade, major, email, clerk_user_id) VALUES (?, 'Bantest', 'Cincinnati', 'OH', "
                + "(SELECT min(id) FROM university), 'Senior', 'History', ?, ?)", firstName, email, subject);
        return jdbc.queryForObject("SELECT id FROM student WHERE email = ?", Long.class, email);
    }

    /** A token that passes the platform's own gate: a real address and a verified second factor. */
    private static Jwt token(String subject, String email) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(subject).claim("email", email)
                .claim("fva", List.of(0, 0))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    private static Jwt admin() {
        return token(ADMIN_SUBJECT, ADMIN_EMAIL);
    }

    private static Jwt student() {
        return token(STUDENT_SUBJECT, STUDENT_EMAIL);
    }

    private boolean mayUseTheApi(Jwt token) {
        return converter.convert(token).getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_STUDENT"));
    }

    private List<String> directoryFirstNames() {
        return directory.find(StudentRequest.builder().lastName("Bantest").build()).stream()
                .map(found -> found.getFirstName()).sorted().toList();
    }

    private String role(String email) {
        List<String> roles = jdbc.queryForList("SELECT role FROM app_user WHERE email = ?", String.class, email);
        return roles.isEmpty() ? null : roles.get(0);
    }

    private int count(String table, String column, String email) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, email);
    }

    private static void assertStatus(HttpStatus expected, Executable action) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, action).getStatusCode());
    }

    // ── What a ban does ──────────────────────────────────────────────────────────

    @Test
    void aBannedStudentIsRefusedByEveryEndpoint_fromTheirNextRequest() {
        assertTrue(mayUseTheApi(student()), "before the ban");

        AdminStudentResponse banned = service.ban(admin(), studentId, false);

        assertTrue(banned.banned());
        assertTrue(bans.isBanned(student()));
        assertFalse(mayUseTheApi(student()), "the same token that worked a moment ago");
        // Nobody else is affected.
        assertTrue(mayUseTheApi(token(BYSTANDER_SUBJECT, BYSTANDER_EMAIL)));
        assertTrue(mayUseTheApi(admin()));
    }

    @Test
    void signingUpAgainOnTheSameAddressDoesNotGetRoundTheBan() {
        service.ban(admin(), studentId, false);

        // A brand-new Clerk account, same address, in any letter case.
        assertFalse(mayUseTheApi(token("user_banTestSecondAccount", STUDENT_EMAIL)));
        assertFalse(mayUseTheApi(token("user_banTestSecondAccount", "BanTest.Student@Mail.UC.edu")));
    }

    @Test
    void changingTheAddressInClerkDoesNotGetRoundTheBan() {
        service.ban(admin(), studentId, false);

        // Same Clerk account, new address: the profile bound to the subject is still the banned one.
        assertFalse(mayUseTheApi(token(STUDENT_SUBJECT, "bantest.new@mail.uc.edu")));
    }

    @Test
    void deletingTheirOwnAccountDoesNotLiftTheBan() {
        service.ban(admin(), studentId, false);

        cleanup.retire(STUDENT_SUBJECT); // what Clerk's user.deleted webhook does

        assertEquals("BANNED", role(STUDENT_EMAIL), "the ban outlives the profile");
        assertFalse(mayUseTheApi(token("user_banTestSecondAccount", STUDENT_EMAIL)));
    }

    @Test
    void aBannedStudentDisappearsFromTheDirectory_andIsStillVisibleToAdministrators() {
        assertEquals(List.of("Banned", "Bystander"), directoryFirstNames());

        service.ban(admin(), studentId, false);

        assertEquals(List.of("Bystander"), directoryFirstNames());
        AdminStudentResponse listed = service.search(admin(), STUDENT_EMAIL, 0, 20).items().get(0);
        assertTrue(listed.banned(), "the account list marks it");
        assertFalse(service.search(admin(), BYSTANDER_EMAIL, 0, 20).items().get(0).banned());
    }

    @Test
    void banningTwiceIsHarmless() {
        service.ban(admin(), studentId, false);
        service.ban(admin(), studentId, false);

        assertEquals(1, count("app_user", "email", STUDENT_EMAIL));
        assertEquals("BANNED", role(STUDENT_EMAIL));
    }

    // ── Lifting a ban ────────────────────────────────────────────────────────────

    @Test
    void liftingABanGivesTheAccountBackExactlyAsItWas() {
        service.ban(admin(), studentId, false);

        service.liftBan(admin(), STUDENT_EMAIL);

        assertEquals("USER", role(STUDENT_EMAIL));
        assertTrue(mayUseTheApi(student()));
        assertEquals(List.of("Banned", "Bystander"), directoryFirstNames());
        assertFalse(service.get(admin(), studentId).banned());
        assertEquals(STUDENT_SUBJECT, jdbc.queryForObject(
                "SELECT clerk_user_id FROM student WHERE id = ?", String.class, studentId));
    }

    @Test
    void liftingABanThatDoesNotExistIs404() {
        assertThrows(SearchNotFoundException.class, () -> service.liftBan(admin(), BYSTANDER_EMAIL));
        assertThrows(SearchNotFoundException.class, () -> service.liftBan(admin(), "nobody@mail.uc.edu"));
        // An administrator's role is not a ban, and is not something this can remove.
        assertThrows(SearchNotFoundException.class, () -> service.liftBan(admin(), ADMIN_EMAIL));
        assertEquals("ADMIN", role(ADMIN_EMAIL));
    }

    // ── Delete and ban ───────────────────────────────────────────────────────────

    @Test
    void deleteAndBan_removesTheAccountAndKeepsTheAddressOut() {
        service.delete(admin(), studentId, true, false);

        assertEquals(0, count("student", "email", STUDENT_EMAIL));
        assertEquals("BANNED", role(STUDENT_EMAIL));
        // They sign up again with the same address: a new Clerk account, refused.
        assertFalse(mayUseTheApi(token("user_banTestSecondAccount", STUDENT_EMAIL)));

        AdminBanResponse listed = service.bans(admin(), 0, 50).items().stream()
                .filter(ban -> ban.email().equals(STUDENT_EMAIL)).findFirst().orElseThrow();
        assertNull(listed.studentId(), "banned, with no profile left");
    }

    @Test
    void deleteWithoutBan_stillLetsThemSignUpAgain() {
        service.delete(admin(), studentId, false, false);

        assertNull(role(STUDENT_EMAIL));
        assertTrue(mayUseTheApi(token("user_banTestSecondAccount", STUDENT_EMAIL)));
    }

    @Test
    void liftingABanOnADeletedAccount_freesTheAddressForSignUp() {
        service.delete(admin(), studentId, true, false);

        service.liftBan(admin(), STUDENT_EMAIL);

        // Not left as a USER row: sign-up refuses any address app_user still holds.
        assertNull(role(STUDENT_EMAIL));
        assertTrue(mayUseTheApi(token("user_banTestSecondAccount", STUDENT_EMAIL)));
    }

    @Test
    void ifTheDeleteFails_theAddressIsNotBannedEither() {
        when(clerk.delete(any(), any())).thenReturn(Outcome.UNCONFIRMED);

        assertStatus(HttpStatus.BAD_GATEWAY, () -> service.delete(admin(), studentId, true, true));

        assertEquals("USER", role(STUDENT_EMAIL));
        assertEquals(1, count("student", "email", STUDENT_EMAIL));
    }

    @Test
    void theBanListShowsWhoIsBanned_withTheirProfileWhenTheyStillHaveOne() {
        service.ban(admin(), studentId, false);

        AdminBanResponse listed = service.bans(admin(), 0, 50).items().stream()
                .filter(ban -> ban.email().equals(STUDENT_EMAIL)).findFirst().orElseThrow();

        assertEquals(studentId, listed.studentId());
        assertEquals("Banned Bantest", listed.name());
        assertTrue(service.bans(admin(), 0, 50).items().stream().noneMatch(b -> b.email().equals(BYSTANDER_EMAIL)));
        assertEquals(1, service.bans(admin(), 0, 1).size());
    }

    // ── Removing what they posted ────────────────────────────────────────────────

    private void postContentAs(String subject, String email) {
        jdbc.update("INSERT INTO listing (seller_email, seller_subject, title, category, listing_type, status, "
                + "created_at, updated_at) VALUES (?, ?, 'Bantest listing', 'BOOKS', 'SELL', 'AVAILABLE', "
                + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", email, subject);
        jdbc.update("INSERT INTO post (author_email, author_subject, content, pinned, created_at) "
                + "VALUES (?, ?, 'Bantest post', false, CURRENT_TIMESTAMP)", email, subject);
        jdbc.update("INSERT INTO event (title, starts_at, created_by_email, created_by_subject, created_at) "
                + "VALUES ('Bantest event', CURRENT_TIMESTAMP, ?, ?, CURRENT_TIMESTAMP)", email, subject);
        jdbc.update("INSERT INTO anonymous_request (requester_email, requester_subject, category, description, "
                + "status, created_at) VALUES (?, ?, 'OTHER', 'Bantest request', 'OPEN', CURRENT_TIMESTAMP)",
                email, subject);
    }

    private int contentOf(String email) {
        return count("listing", "seller_email", email) + count("post", "author_email", email)
                + count("post_comment", "author_email", email) + count("event", "created_by_email", email)
                + count("anonymous_request", "requester_email", email);
    }

    @Test
    void banWithRemoveContent_takesDownEverythingTheyPosted_andNothingOfAnyoneElses() {
        postContentAs(STUDENT_SUBJECT, STUDENT_EMAIL);
        postContentAs(BYSTANDER_SUBJECT, BYSTANDER_EMAIL);
        // The banned student commented on the bystander's post, and the bystander on theirs.
        Long bystanderPost = jdbc.queryForObject("SELECT id FROM post WHERE author_email = ?", Long.class, BYSTANDER_EMAIL);
        Long studentPost = jdbc.queryForObject("SELECT id FROM post WHERE author_email = ?", Long.class, STUDENT_EMAIL);
        jdbc.update("INSERT INTO post_comment (post_id, author_email, author_subject, content, created_at) "
                + "VALUES (?, ?, ?, 'Bantest comment', CURRENT_TIMESTAMP)", bystanderPost, STUDENT_EMAIL, STUDENT_SUBJECT);
        jdbc.update("INSERT INTO post_comment (post_id, author_email, author_subject, content, created_at) "
                + "VALUES (?, ?, ?, 'Bystander reply', CURRENT_TIMESTAMP)", studentPost, BYSTANDER_EMAIL, BYSTANDER_SUBJECT);

        service.ban(admin(), studentId, true);

        assertEquals(0, contentOf(STUDENT_EMAIL));
        // The bystander keeps their listing, post, event and request. Their one reply went
        // with the post it was under, as it would if the author had deleted that post.
        assertEquals(4, contentOf(BYSTANDER_EMAIL));
    }

    @Test
    void banWithoutRemoveContent_leavesTheirPostsUp() {
        postContentAs(STUDENT_SUBJECT, STUDENT_EMAIL);

        service.ban(admin(), studentId, false);

        assertEquals(4, contentOf(STUDENT_EMAIL));
    }

    @Test
    void deleteWithRemoveContent_alsoTakesTheirPostsDown() {
        postContentAs(STUDENT_SUBJECT, STUDENT_EMAIL);

        service.delete(admin(), studentId, false, true);

        assertEquals(0, contentOf(STUDENT_EMAIL));
        assertEquals(0, count("student", "email", STUDENT_EMAIL));
    }

    // ── Who may ban ──────────────────────────────────────────────────────────────

    @Test
    void aStudentCannotBanLiftOrListBans() {
        assertStatus(HttpStatus.FORBIDDEN, () -> service.ban(student(), studentId, true));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.liftBan(student(), STUDENT_EMAIL));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.bans(student(), 0, 20));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.userReports(student(), 0, 20));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.dismissUserReport(student(), 1));

        assertEquals("USER", role(STUDENT_EMAIL));
    }

    @Test
    void anAdministratorCannotBeBanned_includingByThemselves() {
        jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE email = ?", STUDENT_EMAIL);

        assertStatus(HttpStatus.CONFLICT, () -> service.ban(admin(), studentId, true));
        assertStatus(HttpStatus.CONFLICT, () -> service.ban(student(), studentId, false));

        assertEquals("ADMIN", role(STUDENT_EMAIL));
        verifyNoInteractions(clerk);
    }

    @Test
    void banningAMissingAccountIs404() {
        assertThrows(SearchNotFoundException.class, () -> service.ban(admin(), 999_999L, false));
    }

    // ── Reports about students ───────────────────────────────────────────────────

    @Test
    void reportsAboutStudentsAreListedNewestFirst_andCanBeDismissed() {
        jdbc.update("INSERT INTO user_report (reporter_email, reported_email, reason, created_at) "
                + "VALUES (?, ?, 'Bantest: sent abusive messages', CURRENT_TIMESTAMP)", BYSTANDER_EMAIL, STUDENT_EMAIL);

        AdminUserReportResponse report = service.userReports(admin(), 0, 50).items().stream()
                .filter(found -> found.reason().startsWith("Bantest")).findFirst().orElseThrow();
        assertEquals(STUDENT_EMAIL, report.reportedEmail());
        assertEquals(BYSTANDER_EMAIL, report.reporterEmail());

        service.dismissUserReport(admin(), report.id());

        assertEquals(0, count("user_report", "reporter_email", BYSTANDER_EMAIL));
        assertThrows(SearchNotFoundException.class, () -> service.dismissUserReport(admin(), report.id()));
    }
}
