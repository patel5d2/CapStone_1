package com.jonathansoriano.enterprisedevgroupproject.service;

import com.jonathansoriano.enterprisedevgroupproject.PostgresTestConfiguration;
import com.jonathansoriano.enterprisedevgroupproject.domain.EditStudentDetailsRequest;
import com.jonathansoriano.enterprisedevgroupproject.domain.ProfileFieldBounds;
import com.jonathansoriano.enterprisedevgroupproject.domain.StudentSignupRequest;
import com.jonathansoriano.enterprisedevgroupproject.dto.StudentAccountDetailsDto;
import com.jonathansoriano.enterprisedevgroupproject.repository.StudentRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S1-06: the new profile fields survive a round trip on Postgres, every bound is a
 * field-level rejection rather than a database error, and the school cannot be moved
 * through the request body.
 *
 * <p>Validation is exercised through the {@link Validator} directly rather than through
 * MockMvc because that is where the bound lives; what matters is that an over-length value
 * is caught before it reaches the driver, since the alternative is the
 * {@code DataIntegrityViolationException} that the catch-all turns into an opaque 500.
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
@ExtendWith(SpringExtension.class)
class ProfileFieldsTest {

    private static final String EMAIL = "profile.fields@mail.uc.edu";

    @Autowired
    StudentService studentService;
    @Autowired
    StudentRepository studentRepository;
    @Autowired
    Validator validator;
    @Autowired
    JdbcTemplate jdbc;

    private static StudentSignupRequest.StudentSignupRequestBuilder signup() {
        return StudentSignupRequest.builder()
                .firstName("Profile").lastName("Fields")
                .residentCity("Cincinnati").residentState("OH")
                .universityId(1).grade("Senior").major("Information Technology")
                .email(EMAIL);
    }

    private static EditStudentDetailsRequest.EditStudentDetailsRequestBuilder edit() {
        return EditStudentDetailsRequest.builder()
                .firstName("Profile").lastName("Fields")
                .residentCity("Cincinnati").residentState("OH")
                .universityId(1).grade("Senior").major("Information Technology")
                .email(EMAIL);
    }

    private <T> Set<ConstraintViolation<T>> violations(T request) {
        return validator.validate(request);
    }

    private static boolean mentions(Set<? extends ConstraintViolation<?>> violations, String field) {
        return violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field));
    }

    @Test
    void newFieldsSurviveARoundTripOnPostgres() {
        studentRepository.insertNewStudent(signup()
                .graduationYear(2027)
                .bio("Third-year IT student, happy to trade textbooks.")
                .photoUrl("https://example.test/me.jpg")
                .build(), "user_profileFields");

        StudentAccountDetailsDto stored = studentRepository.findByEmail(EMAIL).orElseThrow();

        assertEquals(2027, stored.getGraduationYear());
        assertEquals("Third-year IT student, happy to trade textbooks.", stored.getBio());
        assertEquals("https://example.test/me.jpg", stored.getPhotoUrl());
    }

    @Test
    void aBioAtTheLimitIsAccepted() {
        // The boundary itself must pass, or the bound is narrower than the column.
        assertTrue(violations(edit().bio("x".repeat(ProfileFieldBounds.BIO_MAX)).build()).isEmpty());
    }

    @Test
    void anOverLongBioIsAFieldLevelRejection() {
        Set<ConstraintViolation<EditStudentDetailsRequest>> found =
                violations(edit().bio("x".repeat(ProfileFieldBounds.BIO_MAX + 1)).build());

        assertTrue(mentions(found, "bio"), "the rejection must name the field, not fail at the driver");
    }

    @Test
    void anOverLongSurnameIsAFieldLevelRejection() {
        // The defect this story exists to prevent: an ordinary long surname used to reach
        // Postgres and come back as an opaque 500.
        Set<ConstraintViolation<EditStudentDetailsRequest>> found =
                violations(edit().lastName("x".repeat(101)).build());

        assertTrue(mentions(found, "lastName"));
    }

    @Test
    void aGraduationYearOutsideFourDigitsIsRejected() {
        assertTrue(mentions(violations(edit().graduationYear(202).build()), "graduationYear"));
        assertTrue(mentions(violations(edit().graduationYear(99999).build()), "graduationYear"));
        assertFalse(mentions(violations(edit().graduationYear(2027).build()), "graduationYear"));
    }

    @Test
    void aGraduationYearIsOptional() {
        // A profile is completed over time; an empty field is not an invalid one.
        assertTrue(violations(edit().graduationYear(null).bio(null).photoUrl(null).build()).isEmpty());
    }

    @Test
    void aPhotoUrlThatIsNotHttpsIsRejected() {
        assertTrue(mentions(violations(edit().photoUrl("http://tracker.test/pixel.gif").build()), "photoUrl"));
        assertTrue(mentions(violations(edit().photoUrl("javascript:alert(1)").build()), "photoUrl"));
        assertFalse(mentions(violations(edit().photoUrl("https://example.test/me.jpg").build()), "photoUrl"));
    }

    @Test
    void theRequestBodyCannotMoveAStudentToAnotherSchool() {
        studentRepository.insertNewStudent(signup().build(), "user_profileFields");
        Long before = jdbc.queryForObject(
                "SELECT university_id FROM student WHERE email = ?", Long.class, EMAIL);

        // A signed-in student editing their own profile, asking for a different school.
        studentService.updateStudent(EMAIL, edit().universityId(before.intValue() + 1).bio("moved?").build());

        Long after = jdbc.queryForObject(
                "SELECT university_id FROM student WHERE email = ?", Long.class, EMAIL);

        assertEquals(before, after, "the school must not be reassignable through the request body");
        // ...while the fields the student may edit did change, so this is not just a no-op.
        assertEquals("moved?", studentRepository.findByEmail(EMAIL).orElseThrow().getBio());
    }
}
