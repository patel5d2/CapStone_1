package com.jonathansoriano.enterprisedevgroupproject;

import com.jonathansoriano.enterprisedevgroupproject.security.ClerkJwtAuthenticationConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Walks every call the frontend makes (frontend/src/pages, hooks), with the bodies it
 * sends, through the full stack: security chain, the real Clerk converter and access
 * policy, controllers, services and the H2 database seeded by data.sql. Only the Clerk
 * signature check is skipped. If a page's call stops matching the backend, this fails
 * naming the method and path.
 */
@SpringBootTest(properties = "CLOUDINARY_URL=")
@AutoConfigureMockMvc
class FrontendEndpointsSmokeTest {

    private static final String ALICE = "alice.smoke@gmail.com";
    private static final String BOB = "bob.smoke@outlook.com";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ClerkJwtAuthenticationConverter converter;
    @Autowired
    private ObjectMapper json;

    @Test
    void everyFrontendCallReachesAWorkingEndpoint() throws Exception {
        // Profile.tsx + useSchools.ts
        long schoolId = call(ALICE, HttpMethod.GET, "/api/schools", null, 200).get(0).get("id").asLong();
        call(ALICE, HttpMethod.GET, "/student/profile", null, 404); // before first save
        call(ALICE, HttpMethod.POST, "/student", profile("Alice", schoolId), 201);
        call(BOB, HttpMethod.POST, "/student", profile("Bob", schoolId), 201);
        assertThat(call(ALICE, HttpMethod.GET, "/student/profile", null, 200).get("universityId").asLong())
                .isEqualTo(schoolId);
        call(ALICE, HttpMethod.PUT, "/student/profile", profile("Alice", schoolId), 200);
        MockHttpServletResponse upload = mvc.perform(multipart("/api/profile-images")
                .file(new MockMultipartFile("file", "a.png", "image/png", ONE_PIXEL_PNG))
                .with(as(ALICE))).andReturn().getResponse();
        // CLOUDINARY_URL unset: the endpoint answers with the message Profile.tsx shows.
        assertThat(upload.getStatus()).as(upload.getContentAsString()).isEqualTo(503);

        // Directory.tsx
        long bobId = call(ALICE, HttpMethod.GET, "/student?firstName=Bob", null, 200).get(0).get("id").asLong();
        call(ALICE, HttpMethod.POST, "/api/students/" + bobId + "/conversation", null, 200);

        // Marketplace.tsx
        Map<String, Object> listing = Map.of("title", "Calc textbook", "description", "Like new",
                "category", "BOOKS", "listingType", "SELL", "price", 25, "schoolId", schoolId,
                "photoUrls", List.of("https://example.com/book.jpg"));
        long listingId = call(ALICE, HttpMethod.POST, "/api/marketplace/listings", listing, 201).get("id").asLong();
        call(BOB, HttpMethod.GET, "/api/marketplace/listings", null, 200);
        call(ALICE, HttpMethod.GET, "/api/marketplace/listings/" + listingId, null, 200);
        call(ALICE, HttpMethod.PUT, "/api/marketplace/listings/" + listingId, listing, 200);
        call(ALICE, HttpMethod.GET, "/api/marketplace/my-listings", null, 200);
        call(BOB, HttpMethod.POST, "/api/marketplace/listings/" + listingId + "/favorite", null, 204);
        assertThat(call(BOB, HttpMethod.GET, "/api/marketplace/favorites", null, 200)).hasSize(1);
        call(BOB, HttpMethod.DELETE, "/api/marketplace/listings/" + listingId + "/favorite", null, 204);
        call(BOB, HttpMethod.POST, "/api/marketplace/listings/" + listingId + "/report", Map.of("reason", "spam"), 204);

        // Messages.tsx (a listing chat, then a direct one)
        long chatId = call(BOB, HttpMethod.POST, "/api/messages/conversations",
                Map.of("listingId", listingId), 201).get("id").asLong();
        call(BOB, HttpMethod.POST, "/api/messages/conversations/" + chatId + "/messages", Map.of("content", "Still available?"), 201);
        call(ALICE, HttpMethod.GET, "/api/messages/conversations", null, 200);
        assertThat(call(ALICE, HttpMethod.GET, "/api/messages/conversations/" + chatId + "/messages", null, 200)).hasSize(1);
        call(ALICE, HttpMethod.POST, "/api/messages/conversations/" + chatId + "/read", null, 204);
        call(ALICE, HttpMethod.POST, "/api/messages/conversations", Map.of("recipientEmail", BOB), 201);
        call(ALICE, HttpMethod.POST, "/api/users/block", Map.of("email", BOB), 204);
        assertThat(call(ALICE, HttpMethod.GET, "/api/users/block", null, 200).toString()).contains(BOB);
        call(ALICE, HttpMethod.DELETE, "/api/users/block/" + URLEncoder.encode(BOB, StandardCharsets.UTF_8), null, 204);
        call(ALICE, HttpMethod.POST, "/api/users/report", Map.of("email", BOB, "reason", "rude"), 204);

        call(ALICE, HttpMethod.POST, "/api/marketplace/listings/" + listingId + "/sold", null, 200);
        call(ALICE, HttpMethod.DELETE, "/api/marketplace/listings/" + listingId, null, 204);

        // Community.tsx: posts, comments, groups, events
        long postId = call(ALICE, HttpMethod.POST, "/api/community/posts", Map.of("content", "Hello campus"), 201)
                .get("id").asLong();
        call(BOB, HttpMethod.GET, "/api/community/posts", null, 200);
        call(BOB, HttpMethod.POST, "/api/community/posts/" + postId + "/like", null, 204);
        call(BOB, HttpMethod.DELETE, "/api/community/posts/" + postId + "/like", null, 204);
        call(BOB, HttpMethod.POST, "/api/community/posts/" + postId + "/comments", Map.of("content", "Hi!"), 201);
        assertThat(call(ALICE, HttpMethod.GET, "/api/community/posts/" + postId + "/comments", null, 200)).hasSize(1);
        call(ALICE, HttpMethod.POST, "/api/community/posts/" + postId + "/pin", null, 204);
        call(ALICE, HttpMethod.DELETE, "/api/community/posts/" + postId, null, 204);

        long groupId = call(ALICE, HttpMethod.POST, "/api/community/groups", Map.of("name", "CS majors",
                "description", "", "type", "MAJOR", "relatedValue", "Computer Science", "schoolId", schoolId), 201)
                .get("id").asLong();
        call(BOB, HttpMethod.GET, "/api/community/groups", null, 200);
        call(BOB, HttpMethod.POST, "/api/community/groups/" + groupId + "/join", null, 204);
        assertThat(call(BOB, HttpMethod.GET, "/api/community/groups/mine", null, 200)).hasSize(1);
        call(BOB, HttpMethod.POST, "/api/community/groups/" + groupId + "/leave", null, 204);

        call(ALICE, HttpMethod.POST, "/api/community/events", Map.of("title", "Study night", "description", "",
                "startsAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(), "location", "Library",
                "schoolId", schoolId), 201);
        call(BOB, HttpMethod.GET, "/api/community/events", null, 200);

        // Support.tsx
        call(ALICE, HttpMethod.GET, "/api/support/resources?schoolId=" + schoolId, null, 200);
        long requestId = call(ALICE, HttpMethod.POST, "/api/support/requests", Map.of("category", "FOOD_PANTRY",
                "description", "Groceries this week", "schoolId", schoolId), 201).get("id").asLong();
        call(BOB, HttpMethod.GET, "/api/support/requests?status=OPEN", null, 200);
        assertThat(call(ALICE, HttpMethod.GET, "/api/support/requests/mine", null, 200)).hasSize(1);
        call(BOB, HttpMethod.POST, "/api/support/requests/" + requestId + "/fulfill", null, 204);
    }

    private Map<String, Object> profile(String firstName, long schoolId) {
        return Map.of("firstName", firstName, "lastName", "Smoke", "residentCity", "Cincinnati",
                "residentState", "OH", "universityId", schoolId, "grade", "Junior", "major", "Computer Science",
                "email", "ignored@example.com", "visibility", Map.of("showMajor", true,
                        "showGraduationYear", false, "showBio", false, "showPhoto", false));
    }

    /** A Clerk session token as the frontend sends it, run through the app's own converter. */
    private org.springframework.test.web.servlet.request.RequestPostProcessor as(String email) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256")
                .subject("user_" + email.substring(0, email.indexOf('.')))
                .claim("email", email)
                .claim("fva", List.of(0, 0)) // second factor completed, as after MFA setup
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        return authentication(converter.convert(jwt));
    }

    private JsonNode call(String email, HttpMethod method, String path, Object body, int expected) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, URI.create(path)).with(as(email));
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MockHttpServletResponse response = mvc.perform(builder).andReturn().getResponse();
        String text = response.getContentAsString();
        assertThat(response.getStatus()).as("%s %s -> %s", method, path, text).isEqualTo(expected);
        return text.startsWith("{") || text.startsWith("[") ? json.readTree(text) : null;
    }

    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==");
}
