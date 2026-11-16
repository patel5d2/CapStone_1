package com.jonathansoriano.enterprisedevgroupproject.admin;

import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminAccessResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminPageResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminStudentResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminStudentUpdateRequest;
import com.jonathansoriano.enterprisedevgroupproject.exception.SearchNotFoundException;
import com.jonathansoriano.enterprisedevgroupproject.model.StudentAccountDetails;
import com.jonathansoriano.enterprisedevgroupproject.security.ClerkAccountDeletion;
import com.jonathansoriano.enterprisedevgroupproject.security.ClerkAccountDeletion.Outcome;
import com.jonathansoriano.enterprisedevgroupproject.service.StudentService;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Account management against the real schema and mock data, with only Clerk replaced.
 *
 * <p>Not {@code @Transactional}: deleting an account commits in a transaction of its own
 * ({@code DeletedAccountCleanup}), which cannot see rows a test transaction has not
 * committed. The rows are committed here and removed again afterwards.
 */
@SpringBootTest
class AdminAccountServiceTest {

    private static final String ADMIN_SUBJECT = "user_adminTestAdmin";
    private static final String ADMIN_EMAIL = "admintest.admin@mail.uc.edu";
    private static final String STUDENT_SUBJECT = "user_adminTestStudent";
    private static final String STUDENT_EMAIL = "admintest.student@mail.uc.edu";
    /** A directory row nobody has signed in to, like the demo data. */
    private static final String UNBOUND_EMAIL = "admintest.unbound@mail.uc.edu";
    private static final String[] EMAILS = {ADMIN_EMAIL, STUDENT_EMAIL, UNBOUND_EMAIL};

    @Autowired
    AdminAccountService service;
    @Autowired
    StudentService students;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    ClerkAccountDeletion clerk;

    private long studentId;
    private long unboundId;

    @BeforeEach
    void seed() {
        clear();
        // A dedicated administrator account: the role, and no student profile.
        jdbc.update("INSERT INTO app_user (role, email) VALUES ('ADMIN', ?)", ADMIN_EMAIL);
        studentId = insertStudent("Zelda", "Admintest", STUDENT_EMAIL, STUDENT_SUBJECT);
        unboundId = insertStudent("Yusuf", "Admintest", UNBOUND_EMAIL, null);
        jdbc.update("UPDATE student SET photo_url = 'https://example.com/zelda.png' WHERE id = ?", studentId);
        jdbc.update("INSERT INTO profile_privacy (student_id, show_major) VALUES (?, false)", studentId);
    }

    @AfterEach
    void clear() {
        for (String email : EMAILS) {
            jdbc.update("DELETE FROM clerk_identity WHERE email = ?", email);
            jdbc.update("DELETE FROM student WHERE email = ?", email);
            jdbc.update("DELETE FROM app_user WHERE email = ?", email);
        }
    }

    private long insertStudent(String firstName, String lastName, String email, String subject) {
        jdbc.update("INSERT INTO app_user (role, email) VALUES ('USER', ?)", email);
        jdbc.update("INSERT INTO student (first_name, last_name, resident_city, resident_state, university_id, "
                + "grade, major, email, clerk_user_id) VALUES (?, ?, 'Cincinnati', 'OH', "
                + "(SELECT min(id) FROM university), 'Senior', 'History', ?, ?)", firstName, lastName, email, subject);
        return jdbc.queryForObject("SELECT id FROM student WHERE email = ?", Long.class, email);
    }

    private static Jwt token(String subject, String email) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(subject).claim("email", email)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    private static Jwt admin() {
        return token(ADMIN_SUBJECT, ADMIN_EMAIL);
    }

    private static Jwt student() {
        return token(STUDENT_SUBJECT, STUDENT_EMAIL);
    }

    private Map<String, Object> row(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM student WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int count(String table, String email) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE email = ?", Integer.class, email);
    }

    private static AdminStudentUpdateRequest.AdminStudentUpdateRequestBuilder edit() {
        return AdminStudentUpdateRequest.builder().firstName("Zelda").lastName("Renamed").residentCity("Norwood")
                .residentState("KY").universityId(2L).grade("Junior").major("Physics")
                .socialMediaLink("https://example.com/zelda").graduationYear(2028).bio("Edited by an administrator.");
    }

    private static void assertStatus(HttpStatus expected, Executable action) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, action).getStatusCode());
    }

    // ── Who may do this ──────────────────────────────────────────────────────────

    @Test
    void aStudentIsRefusedEveryAction_andNothingChanges() {
        Map<String, Object> before = row(unboundId);

        assertStatus(HttpStatus.FORBIDDEN, () -> service.search(student(), "", 0, 20));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.get(student(), unboundId));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.update(student(), unboundId, edit().build()));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.delete(student(), unboundId, false, false));
        // Their own account is no exception: this is not how a student edits a profile.
        assertStatus(HttpStatus.FORBIDDEN, () -> service.update(student(), studentId, edit().build()));

        assertEquals(before, row(unboundId));
        verifyNoInteractions(clerk);
    }

    @Test
    void anAccountThatDoesNotExistIsStillA403ForAStudent() {
        // The role is checked first, so a student cannot use the answer to probe for ids.
        assertStatus(HttpStatus.FORBIDDEN, () -> service.get(student(), 999_999L));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.delete(student(), 999_999L, false, false));
    }

    @Test
    void theRoleIsReadOnEveryRequest_soTakingItAwayWorksAtOnce() {
        assertEquals(1, service.search(admin(), "yusuf admintest", 0, 20).items().size());

        jdbc.update("UPDATE app_user SET role = 'USER' WHERE email = ?", ADMIN_EMAIL);

        assertStatus(HttpStatus.FORBIDDEN, () -> service.search(admin(), "", 0, 20));
    }

    @Test
    void accessDescribesTheCaller() {
        assertEquals(new AdminAccessResponse(true, false), service.access(admin()),
                "a dedicated administrator account has no student profile");
        assertEquals(new AdminAccessResponse(false, true), service.access(student()));
        assertEquals(new AdminAccessResponse(false, false),
                service.access(token("user_adminTestNobody", "admintest.nobody@mail.uc.edu")));
    }

    // ── Finding and viewing ──────────────────────────────────────────────────────

    @Test
    void searchMatchesPartOfANameOrAnEmail_inAnyCase() {
        assertEquals(List.of("Yusuf", "Zelda"), firstNames(service.search(admin(), "ADMINTEST", 0, 20)));
        assertEquals(List.of("Zelda"), firstNames(service.search(admin(), "zelda admin", 0, 20)));
        assertEquals(List.of("Yusuf"), firstNames(service.search(admin(), "admintest.unbound@", 0, 20)));
    }

    @Test
    void searchShowsWhatAnAdministratorNeedsToTellAccountsApart() {
        AdminStudentResponse bound = service.search(admin(), STUDENT_EMAIL, 0, 20).items().get(0);

        assertEquals(STUDENT_EMAIL, bound.email());
        assertEquals("University of Cincinnati", bound.universityName());
        // The student hid their major from the directory; that choice does not bind an administrator.
        assertEquals("History", bound.major());
        assertTrue(bound.hasSignIn());
        assertFalse(bound.administrator());
        assertFalse(service.get(admin(), unboundId).hasSignIn());
    }

    @Test
    void aBlankSearchListsEveryone_onePageAtATime() {
        AdminPageResponse<AdminStudentResponse> first = service.search(admin(), "  ", 0, 10);

        assertEquals(10, first.items().size());
        assertEquals(35, first.totalItems(), "the 33 mock students and the two added here");
        assertEquals(4, first.totalPages());
        assertEquals(5, service.search(admin(), null, 3, 10).items().size());
    }

    @Test
    void thePageSizeIsBoundedWhateverIsAskedFor() {
        assertEquals(AdminAccountService.MAX_PAGE_SIZE, service.search(admin(), "", 0, 10_000).size());
        assertEquals(1, service.search(admin(), "", 0, 0).size());
        assertEquals(0, service.search(admin(), "", -5, 20).page());
    }

    @Test
    void noMatchAndAPagePastTheEnd_areEmptyPagesNotErrors() {
        AdminPageResponse<AdminStudentResponse> none = service.search(admin(), "no-such-student-anywhere", 0, 20);
        assertTrue(none.items().isEmpty());
        assertEquals(0, none.totalItems());

        assertTrue(service.search(admin(), "", 500, 20).items().isEmpty());
    }

    @Test
    void wildcardsInTheSearchTextMatchThemselves() {
        assertTrue(service.search(admin(), "%", 0, 20).items().isEmpty(), "no name or email contains a percent sign");
        assertTrue(service.search(admin(), "z_lda", 0, 20).items().isEmpty(), "underscore is not 'any character'");
        assertEquals("%!%!_!!x%", AdminAccountService.patternFor("%_!X"));
    }

    @Test
    void aRowPointingAtAMissingSchool_stillListsWithNoSchoolName() {
        // Unavailable data on one account must not break the view or its neighbours.
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            jdbc.update("UPDATE student SET university_id = 999999 WHERE id = ?", unboundId);

            AdminPageResponse<AdminStudentResponse> page = service.search(admin(), "admintest", 0, 20);

            assertEquals(List.of("Yusuf", "Zelda"), firstNames(page));
            assertNull(page.items().get(0).universityName());
            assertEquals("University of Cincinnati", page.items().get(1).universityName());
        } finally {
            jdbc.update("UPDATE student SET university_id = (SELECT min(id) FROM university) WHERE id = ?", unboundId);
            jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    @Test
    void aMissingAccountIs404() {
        assertThrows(SearchNotFoundException.class, () -> service.get(admin(), 999_999L));
    }

    // ── Updating ─────────────────────────────────────────────────────────────────

    @Test
    void anUpdateIsSaved_andTheStudentSeesItInTheirOwnProfile() {
        AdminStudentResponse saved = service.update(admin(), studentId, edit().build());

        assertEquals("Renamed", saved.lastName());
        assertEquals("Northern Kentucky University", saved.universityName());

        // The student experience: what GET /student/profile reads for this student.
        StudentAccountDetails profile = students.findByEmail(STUDENT_EMAIL);
        assertEquals("Renamed", profile.getLastName());
        assertEquals("Norwood", profile.getResidentCity());
        assertEquals("KY", profile.getResidentState());
        assertEquals("Northern Kentucky University", profile.getUniversityName());
        assertEquals("Junior", profile.getGrade());
        assertEquals("Physics", profile.getMajor());
        assertEquals("https://example.com/zelda", profile.getSocialMediaLink());
        assertEquals(2028, profile.getGraduationYear());
        assertEquals("Edited by an administrator.", profile.getBio());
    }

    @Test
    void anUpdateLeavesIdentityPhotoAndPrivacyAlone() {
        service.update(admin(), studentId, edit().build());

        Map<String, Object> after = row(studentId);
        assertEquals(STUDENT_EMAIL, after.get("email"));
        assertEquals(STUDENT_SUBJECT, after.get("clerk_user_id"));
        assertEquals("https://example.com/zelda.png", after.get("photo_url"));
        assertEquals(false, jdbc.queryForObject(
                "SELECT show_major FROM profile_privacy WHERE student_id = ?", Boolean.class, studentId));
        assertEquals("USER", jdbc.queryForObject("SELECT role FROM app_user WHERE email = ?", String.class, STUDENT_EMAIL));
    }

    @Test
    void optionalFieldsCanBeCleared_andTextIsTrimmed() {
        AdminStudentResponse saved = service.update(admin(), studentId,
                edit().firstName("  Zelda  ").socialMediaLink("   ").bio("").graduationYear(null).build());

        assertEquals("Zelda", saved.firstName());
        assertNull(saved.socialMediaLink());
        assertNull(saved.bio());
        assertNull(saved.graduationYear());
    }

    @Test
    void aSchoolThatDoesNotExist_is400_andNothingIsSaved() {
        Map<String, Object> before = row(studentId);

        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.update(admin(), studentId, edit().universityId(999_999L).build()));

        assertEquals(before, row(studentId), "not even the valid fields of a refused update are written");
    }

    @Test
    void updatingAMissingAccountIs404_andNobodyElseIsTouched() {
        Map<String, Object> bystander = row(unboundId);

        assertThrows(SearchNotFoundException.class, () -> service.update(admin(), 999_999L, edit().build()));

        assertEquals(bystander, row(unboundId));
    }

    // ── Deleting ─────────────────────────────────────────────────────────────────

    @Test
    void deletingASignedUpStudent_removesTheirSignInAndTheirAccount() {
        when(clerk.delete(STUDENT_SUBJECT, ADMIN_SUBJECT)).thenReturn(Outcome.REMOVED);

        service.delete(admin(), studentId, false, false);

        verify(clerk).delete(STUDENT_SUBJECT, ADMIN_SUBJECT);
        assertNull(row(studentId));
        assertEquals(0, count("app_user", STUDENT_EMAIL));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM profile_privacy WHERE student_id = ?",
                Integer.class, studentId));
        // No longer an active student account anywhere an administrator or a student looks.
        assertThrows(SearchNotFoundException.class, () -> service.get(admin(), studentId));
        assertThrows(SearchNotFoundException.class, () -> students.findByEmail(STUDENT_EMAIL));
        assertEquals(new AdminAccessResponse(false, false), service.access(student()));
        // Nobody else went with them.
        assertEquals(1, count("student", UNBOUND_EMAIL));
        assertEquals(1, count("app_user", ADMIN_EMAIL));
    }

    @Test
    void ifTheSignInCannotBeRemoved_nothingIsDeleted() {
        Map<String, Object> before = row(studentId);

        when(clerk.delete(any(), any())).thenReturn(Outcome.NOT_CONFIGURED);
        ResponseStatusException noKey = assertThrows(ResponseStatusException.class,
                () -> service.delete(admin(), studentId, false, false));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, noKey.getStatusCode());
        assertEquals(AdminAccountService.NO_CLERK_KEY_MESSAGE, noKey.getReason());

        when(clerk.delete(any(), any())).thenReturn(Outcome.UNCONFIRMED);
        ResponseStatusException unconfirmed = assertThrows(ResponseStatusException.class,
                () -> service.delete(admin(), studentId, false, false));
        assertEquals(HttpStatus.BAD_GATEWAY, unconfirmed.getStatusCode());
        assertEquals(AdminAccountService.CLERK_UNCONFIRMED_MESSAGE, unconfirmed.getReason());

        assertEquals(before, row(studentId));
        assertEquals(1, count("app_user", STUDENT_EMAIL));
    }

    @Test
    void aRowNobodyHasSignedInTo_isDeletedWithoutAskingClerk() {
        service.delete(admin(), unboundId, false, false);

        verifyNoInteractions(clerk);
        assertNull(row(unboundId));
        assertEquals(0, count("app_user", UNBOUND_EMAIL));
        assertEquals(1, count("student", STUDENT_EMAIL));
    }

    @Test
    void aSignInTheWebhookRecordedOnAnUnboundRowsAddress_isRemovedToo() {
        jdbc.update("INSERT INTO clerk_identity (clerk_user_id, email, last_event_id, last_event_at) "
                + "VALUES ('user_adminTestRecorded', ?, 'msg_admintest', CURRENT_TIMESTAMP)", UNBOUND_EMAIL);
        when(clerk.delete("user_adminTestRecorded", ADMIN_SUBJECT)).thenReturn(Outcome.REMOVED);

        service.delete(admin(), unboundId, false, false);

        verify(clerk).delete("user_adminTestRecorded", ADMIN_SUBJECT);
        assertNull(row(unboundId));
        assertEquals(0, count("clerk_identity", UNBOUND_EMAIL));
    }

    @Test
    void anAdministratorAccountCannotBeDeletedHere() {
        // A student who was later made an administrator: the role, and a profile.
        jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE email = ?", STUDENT_EMAIL);

        assertStatus(HttpStatus.CONFLICT, () -> service.delete(admin(), studentId, false, false));
        // ...including by themselves.
        assertStatus(HttpStatus.CONFLICT, () -> service.delete(student(), studentId, false, false));

        verifyNoInteractions(clerk);
        assertEquals(1, count("student", STUDENT_EMAIL));
        assertTrue(service.get(admin(), studentId).administrator(), "the list says why delete is unavailable");
    }

    @Test
    void deletingAMissingAccountIs404() {
        assertThrows(SearchNotFoundException.class, () -> service.delete(admin(), 999_999L, false, false));
        verifyNoInteractions(clerk);
    }

    private static List<String> firstNames(AdminPageResponse<AdminStudentResponse> page) {
        return page.items().stream().map(AdminStudentResponse::firstName).toList();
    }
}
