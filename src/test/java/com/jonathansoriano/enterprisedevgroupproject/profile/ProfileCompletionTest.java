package com.jonathansoriano.enterprisedevgroupproject.profile;

import com.jonathansoriano.enterprisedevgroupproject.domain.*;
import com.jonathansoriano.enterprisedevgroupproject.image.*;
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
    @Autowired ObjectMapper mapper;
    @Autowired jakarta.persistence.EntityManager entities;
    private static final String EMAIL = "completion-test@mail.uc.edu";
    private static final String SUBJECT = "user_completion_test";

    private Jwt jwt(String subject, String email) {
        return Jwt.withTokenValue("test-only").header("alg", "RS256").subject(subject).claim("email", email).build();
    }
    private Jwt owner() { return jwt(SUBJECT, EMAIL); }
    private StudentSignupRequest.StudentSignupRequestBuilder signup() {
        return StudentSignupRequest.builder().firstName("UniqueCompletion").lastName("Student")
                .residentCity("Cincinnati").residentState("OH").universityId(1)
                .grade("Senior").major("DirectoryMajorFixture").email(EMAIL)
                .graduationYear(2027).bio("About this student");
    }
    private EditStudentDetailsRequest.EditStudentDetailsRequestBuilder edit() {
        return EditStudentDetailsRequest.builder().firstName("UniqueCompletion").lastName("Student")
                .residentCity("Cincinnati").residentState("OH").universityId(1)
                .grade("Senior").major("DirectoryMajorFixture").email(EMAIL)
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
    @Test void createAndRefreshRetainsEveryField() {
        String url = uploaded(SUBJECT);
        profiles.create(owner(), signup().photoUrl(url).build());
        var saved = profiles.read(owner());
        assertEquals(2027, saved.getGraduationYear());
        assertEquals("About this student", saved.getBio());
        assertEquals(url, saved.getPhotoUrl());
        assertEquals(1L, saved.getUniversityId());
        assertNotNull(assets.findByUrl(url).orElseThrow().getStudentId());
        assertNull(assets.findByUrl(url).orElseThrow().getDeleteAfter());
    }
    // #112: one visibility rule for every profile, with nothing for a student to switch off.
    @Test void directoryShowsTheSameFieldsForEveryProfileAndNeverContactDetails() {
        String url = uploaded(SUBJECT);
        profiles.create(owner(), signup().photoUrl(url).build());
        var json = mapper.valueToTree(profiles.directory(query()).getFirst());
        assertEquals("DirectoryMajorFixture", json.get("major").asString());
        assertEquals(2027, json.get("graduationYear").asInt());
        assertEquals("About this student", json.get("bio").asString());
        assertEquals(url, json.get("photoUrl").asString());
        assertFalse(json.has("email"));
        assertFalse(json.has("socialMediaLink"));
    }
    @Test void aProfileIsFoundBySearchingItsMajor() {
        profiles.create(owner(), signup().build());
        var found = profiles.directory(StudentRequest.builder().major("DirectoryMajorFixture").build());
        assertEquals(1, found.size());
        assertEquals("UniqueCompletion", found.getFirst().getFirstName());
    }
    @Test void aSavedEditReachesTheDirectoryImmediately() {
        profiles.create(owner(), signup().build());
        profiles.update(owner(), edit().build());
        // The edit is written with JDBC and the directory reads through JPA. This test is one
        // transaction, so drop JPA's cached row, as the next request's own transaction does.
        entities.clear();
        var listed = profiles.directory(query()).getFirst();
        assertEquals("Updated biography", listed.getBio());
        assertEquals(2028, listed.getGraduationYear());
        assertEquals(2028, profiles.read(owner()).getGraduationYear());
    }
    @Test void thereAreNoVisibilityPreferencesToReadOrSend() {
        profiles.create(owner(), signup().build());
        assertFalse(mapper.valueToTree(profiles.read(owner())).has("visibility"));
        // A page loaded before this change still sends the old field; it is ignored, not refused.
        assertDoesNotThrow(() -> mapper.readValue(
                "{\"firstName\":\"Old\",\"visibility\":{\"showMajor\":false}}", EditStudentDetailsRequest.class));
    }
    @Test void anotherSubjectsImageIsRejectedBeforeProfileChanges() {
        profiles.create(owner(), signup().build());
        String url = uploaded("user_another");
        var error = assertThrows(ResponseStatusException.class,
                () -> profiles.update(owner(), edit().photoUrl(url).build()));
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
        profiles.create(owner(), signup().build());
        profiles.update(jwt(SUBJECT, "changed@mail.uc.edu"), edit().universityId(2).build());
        assertEquals(1L, profiles.read(owner()).getUniversityId());
    }
    @Test void anotherAccountCannotReadOrUpdateBoundProfile() {
        profiles.create(owner(), signup().build());
        assertThrows(ResponseStatusException.class, () -> profiles.read(jwt("user_other", EMAIL)));
        assertThrows(ResponseStatusException.class, () -> profiles.update(jwt("user_other", EMAIL), edit().build()));
    }
}
