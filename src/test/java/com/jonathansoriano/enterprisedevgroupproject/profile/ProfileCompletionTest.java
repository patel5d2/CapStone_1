package com.jonathansoriano.enterprisedevgroupproject.profile;

import com.jonathansoriano.enterprisedevgroupproject.domain.*;
import com.jonathansoriano.enterprisedevgroupproject.image.*;
import com.jonathansoriano.enterprisedevgroupproject.profile.dto.ProfileVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ProfileCompletionTest {
    @Autowired ProfileCompletionService profiles;
    @Autowired ImageAssetRepository assets;
    @Autowired ProfilePrivacyRepository privacy;
    @Autowired ObjectMapper mapper;
    private static final String EMAIL = "completion-test@mail.uc.edu";
    private static final String SUBJECT = "user_completion_test";
    private static final ProfileVisibility SHARED = new ProfileVisibility(true, true, true, true);

    private Jwt jwt(String subject, String email) {
        return Jwt.withTokenValue("test-only").header("alg", "RS256").subject(subject).claim("email", email).build();
    }
    private Jwt owner() { return jwt(SUBJECT, EMAIL); }
    private StudentSignupRequest.StudentSignupRequestBuilder signup() {
        return StudentSignupRequest.builder().firstName("UniqueCompletion").lastName("Student")
                .residentCity("Cincinnati").residentState("OH").universityId(1)
                .grade("Senior").major("PrivateMajorFixture").email(EMAIL)
                .graduationYear(2027).bio("About this student");
    }
    private EditStudentDetailsRequest.EditStudentDetailsRequestBuilder edit() {
        return EditStudentDetailsRequest.builder().firstName("UniqueCompletion").lastName("Student")
                .residentCity("Cincinnati").residentState("OH").universityId(1)
                .grade("Senior").major("PrivateMajorFixture").email(EMAIL)
                .graduationYear(2028).bio("Updated biography");
    }
    private StudentRequest query() { return StudentRequest.builder().firstName("UniqueCompletion").build(); }
    private String uploaded(String subject) {
        ImageAsset asset = new ImageAsset();
        asset.setId(UUID.randomUUID().toString());
        asset.setOwnerSubject(subject);
        asset.setPublicId("campusbridge/profiles/" + asset.getId());
        asset.setUrl("https://res.cloudinary.com/test/image/upload/v1/" + asset.getPublicId() + ".webp");
        asset.setDeleteAfter(Instant.now().plusSeconds(86400));
        return assets.saveAndFlush(asset).getUrl();
    }

    @Test void firstVisitBeforeWebhookIsExplicitlyPending() {
        var error = assertThrows(ResponseStatusException.class, () -> profiles.read(owner()));
        assertEquals(404, error.getStatusCode().value());
    }
    @Test void createAndRefreshRetainsEveryFieldAndVisibility() {
        String url = uploaded(SUBJECT);
        profiles.create(owner(), signup().photoUrl(url).visibility(SHARED).build());
        var saved = profiles.read(owner());
        assertEquals(2027, saved.getGraduationYear());
        assertEquals("About this student", saved.getBio());
        assertEquals(url, saved.getPhotoUrl());
        assertEquals(SHARED, saved.getVisibility());
        assertEquals(1L, saved.getUniversityId());
        assertNotNull(assets.findByUrl(url).orElseThrow().getStudentId());
        assertNull(assets.findByUrl(url).orElseThrow().getDeleteAfter());
    }
    @Test void preferencesDefaultPrivateAndContactsNeverSerialize() {
        profiles.create(owner(), signup().build());
        var student = profiles.directory(query()).getFirst();
        var json = mapper.valueToTree(student);
        assertFalse(json.has("email"));
        assertFalse(json.has("socialMediaLink"));
        assertFalse(json.has("major"));
        assertFalse(json.has("bio"));
        assertFalse(json.has("graduationYear"));
        assertEquals(ProfileVisibility.privateByDefault(), profiles.read(owner()).getVisibility());
        assertEquals("PrivateMajorFixture", profiles.read(owner()).getMajor());
    }
    @Test void privateMajorCannotMatchASearch() {
        profiles.create(owner(), signup().build());
        assertTrue(profiles.directory(StudentRequest.builder().major("PrivateMajorFixture").build()).isEmpty());
    }
    @Test void visibilityChangesApplyImmediatelyToSearchAndPayload() {
        profiles.create(owner(), signup().visibility(SHARED).build());
        assertEquals("PrivateMajorFixture", profiles.directory(query()).getFirst().getMajor());
        profiles.update(owner(), edit().visibility(ProfileVisibility.privateByDefault()).build());
        assertNull(profiles.directory(query()).getFirst().getMajor());
        assertNull(profiles.directory(query()).getFirst().getBio());
        assertEquals(2028, profiles.read(owner()).getGraduationYear());
    }
    @Test void anotherSubjectsImageIsRejectedBeforeProfileChanges() {
        profiles.create(owner(), signup().build());
        String url = uploaded("user_another");
        var error = assertThrows(ResponseStatusException.class,
                () -> profiles.update(owner(), edit().photoUrl(url).visibility(SHARED).build()));
        assertEquals(403, error.getStatusCode().value());
        assertEquals("About this student", profiles.read(owner()).getBio());
    }
    @Test void forgedRemoteImageIsRejected() {
        profiles.create(owner(), signup().build());
        assertThrows(ResponseStatusException.class,
                () -> profiles.update(owner(), edit().photoUrl("https://tracking.invalid/photo.png").build()));
    }
    @Test void replacingPhotoQueuesOnlyTheOldImageForDeletion() {
        String first = uploaded(SUBJECT);
        String second = uploaded(SUBJECT);
        profiles.create(owner(), signup().photoUrl(first).build());
        profiles.update(owner(), edit().photoUrl(second).build());
        assertNull(assets.findByUrl(first).orElseThrow().getStudentId());
        assertNotNull(assets.findByUrl(first).orElseThrow().getDeleteAfter());
        assertNotNull(assets.findByUrl(second).orElseThrow().getStudentId());
        assertNull(assets.findByUrl(second).orElseThrow().getDeleteAfter());
    }
    @Test void emailChangeKeepsTheSameProfileAndSchoolIsImmutable() {
        profiles.create(owner(), signup().visibility(SHARED).build());
        profiles.update(jwt(SUBJECT, "changed@mail.uc.edu"), edit().universityId(2).build());
        assertEquals(1L, profiles.read(owner()).getUniversityId());
        assertEquals(SHARED, profiles.read(owner()).getVisibility());
    }
    @Test void anotherAccountCannotReadOrUpdateBoundProfile() {
        profiles.create(owner(), signup().build());
        assertThrows(ResponseStatusException.class, () -> profiles.read(jwt("user_other", EMAIL)));
        assertThrows(ResponseStatusException.class, () -> profiles.update(jwt("user_other", EMAIL), edit().build()));
    }
}
