package com.jonathansoriano.enterprisedevgroupproject.service;

import com.jonathansoriano.enterprisedevgroupproject.domain.StudentSignupRequest;
import com.jonathansoriano.enterprisedevgroupproject.repository.StudentRepository;
import com.jonathansoriano.enterprisedevgroupproject.security.ClerkAccountLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The S1-02 acceptance matrix, run against the real migrations on a disposable
 * PostgreSQL 16: missing subject, a subject with no row, a mismatched subject, and the
 * two cases the story exists for — an email change keeping ownership, and a second
 * account failing to claim it.
 */
@SpringBootTest
@Transactional
@ExtendWith(SpringExtension.class)
class StudentIdentityServiceTest {

    private static final String SUBJECT = "user_owner";
    private static final String OTHER_SUBJECT = "user_intruder";
    private static final String SIGNED_UP_EMAIL = "identity.test@mail.uc.edu";
    /** One of the 33 fabricated people V3 seeds. */
    private static final String SEEDED_DEMO_EMAIL = "sarah.johnson@mail.uc.edu";

    @Autowired
    StudentIdentityService identityService;
    @Autowired
    StudentRepository studentRepository;
    @Autowired
    JdbcTemplate jdbc;
    /** Clerk's answer to "does this account still exist?"; by default, yes. */
    @MockitoBean
    ClerkAccountLookup clerkAccounts;
    /** Mocked because the real one commits in its own transaction, outside this test's. */
    @MockitoBean
    DeletedAccountCleanup deletedAccounts;

    private static Jwt token(String subject, String email) {
        Jwt.Builder builder = Jwt.withTokenValue("clerk-session-token").header("alg", "RS256");
        if (subject != null) {
            builder.subject(subject);
        }
        if (email != null) {
            builder.claim("email", email);
        }
        return builder.claim("iss", "https://clerk.test").build();
    }

    private void createProfileFor(String subject, String email) {
        studentRepository.insertNewStudent(StudentSignupRequest.builder()
                .firstName("Identity").lastName("Test")
                .residentCity("Cincinnati").residentState("OH")
                .universityId(1).grade("Senior").major("Information Technology")
                .email(email).socialMediaLink(null)
                .build(), subject);
    }

    private String storedSubjectFor(String email) {
        return jdbc.queryForObject("SELECT clerk_user_id FROM student WHERE email = ?", String.class, email);
    }

    @Test
    void missingSubject_isUnauthorized() {
        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> identityService.ownerEmailFor(token(null, SIGNED_UP_EMAIL)));

        assertEquals(HttpStatus.UNAUTHORIZED, thrown.getStatusCode());
    }

    @Test
    void missingEmailClaim_isUnauthorized() {
        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> identityService.ownerEmailFor(token(SUBJECT, null)));

        assertEquals(HttpStatus.UNAUTHORIZED, thrown.getStatusCode());
    }

    @Test
    void subjectWithNoStudentRow_fallsBackToTheTokenEmail() {
        // The compatibility window: somebody who has authenticated but never created a
        // directory profile still resolves, so every email-keyed feature keeps working.
        assertEquals(SIGNED_UP_EMAIL, identityService.ownerEmailFor(token(SUBJECT, SIGNED_UP_EMAIL)));
    }

    @Test
    void emailChangedInClerk_ownershipStaysWithTheSubject() {
        createProfileFor(SUBJECT, SIGNED_UP_EMAIL);

        // Same person, new address in Clerk, nothing changed in the database.
        String resolved = identityService.ownerEmailFor(token(SUBJECT, "new.address@mail.uc.edu"));

        // Resolves to the address the data is keyed on, not the one the token carries —
        // which is exactly what stops a rename orphaning the 16 ownership columns.
        assertEquals(SIGNED_UP_EMAIL, resolved);
    }

    @Test
    void anotherSubjectCannotClaimAProfile() {
        createProfileFor(SUBJECT, SIGNED_UP_EMAIL);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> identityService.ownerEmailFor(token(OTHER_SUBJECT, SIGNED_UP_EMAIL)));

        assertEquals(HttpStatus.CONFLICT, thrown.getStatusCode());
        // ...and the refusal did not quietly reassign the row.
        assertEquals(SUBJECT, storedSubjectFor(SIGNED_UP_EMAIL));
        // The owner is alive as far as Clerk says, so their profile is not touched.
        verify(deletedAccounts, never()).retire(any());
    }

    @Test
    void theOwnerDeletedTheirAccount_theProfileGoesAndTheNewAccountStartsFresh() {
        // Delete the account in Clerk, sign up again with the same address: a new subject
        // on the old profile's email. It used to be refused on every request, for good.
        createProfileFor(SUBJECT, SIGNED_UP_EMAIL);
        when(clerkAccounts.isDeleted(SUBJECT, OTHER_SUBJECT)).thenReturn(true);

        String resolved = identityService.ownerEmailFor(token(OTHER_SUBJECT, SIGNED_UP_EMAIL));

        // Resolves like anyone with no profile yet, and the dead account's profile is retired.
        assertEquals(SIGNED_UP_EMAIL, resolved);
        verify(deletedAccounts).retire(SUBJECT);
    }

    @Test
    void theOwnerThemselves_neverTriggersALookup() {
        createProfileFor(SUBJECT, SIGNED_UP_EMAIL);

        identityService.ownerEmailFor(token(SUBJECT, SIGNED_UP_EMAIL));

        verify(clerkAccounts, never()).isDeleted(any(), any());
    }

    @Test
    void readingDoesNotBindALegacyRowToTheCaller() {
        // Rule 4 of the backfill plan, and the reason binding never happens on read: the
        // seeded demo people sit on plausible addresses, so a real student signing in
        // with a matching one must not inherit a fabricated profile.
        assertEquals(SEEDED_DEMO_EMAIL, identityService.ownerEmailFor(token(SUBJECT, SEEDED_DEMO_EMAIL)));

        assertNull(storedSubjectFor(SEEDED_DEMO_EMAIL),
                "a legacy row must stay unassigned until its owner proves it");
    }
}
