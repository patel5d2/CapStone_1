package com.jonathansoriano.enterprisedevgroupproject.admin;

import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminGroupResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminListingResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminPostResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminSupportRequestResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminSupportResourceRequest;
import com.jonathansoriano.enterprisedevgroupproject.exception.SearchNotFoundException;
import com.jonathansoriano.enterprisedevgroupproject.security.ClerkAccountDeletion;
import com.jonathansoriano.enterprisedevgroupproject.support.RequestStatus;
import com.jonathansoriano.enterprisedevgroupproject.support.SupportCategory;
import com.jonathansoriano.enterprisedevgroupproject.support.SupportService;
import com.jonathansoriano.enterprisedevgroupproject.support.dto.SupportResourceResponse;
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
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Moderation of Marketplace, Community and Support (#113), against the real schema, so the
 * cascades a removal relies on are the database's own.
 */
@SpringBootTest
class AdminModerationServiceTest {

    private static final String ADMIN_EMAIL = "modtest.admin@mail.uc.edu";
    private static final String SELLER = "modtest.seller@mail.uc.edu";
    private static final String OTHER = "modtest.other@mail.uc.edu";

    @Autowired
    AdminModerationService service;
    @Autowired
    SupportService studentSupport;
    @Autowired
    JdbcTemplate jdbc;
    // Not used here; declared so this class shares one cached Spring context with the other admin tests.
    @MockitoBean
    ClerkAccountDeletion clerk;

    private long reportedListing;
    private long cleanListing;
    private long feedPost;
    private long group;
    private long groupPost;
    private long comment;
    private long pastEvent;
    private long request;

    @BeforeEach
    void seed() {
        clear();
        jdbc.update("INSERT INTO app_user (role, email) VALUES ('ADMIN', ?)", ADMIN_EMAIL);
        jdbc.update("INSERT INTO app_user (role, email) VALUES ('USER', ?)", SELLER);

        reportedListing = listing("Modtest counterfeit tickets", SELLER);
        cleanListing = listing("Modtest calculus textbook", OTHER);
        jdbc.update("INSERT INTO listing_photo (listing_id, photo_url) VALUES (?, 'https://example.com/modtest-1.png')", reportedListing);
        jdbc.update("INSERT INTO listing_favorite (listing_id, user_email, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)", reportedListing, OTHER);
        for (String reason : List.of("Modtest: looks like a scam", "Modtest: fake tickets")) {
            jdbc.update("INSERT INTO listing_report (listing_id, reporter_email, reason, created_at) "
                    + "VALUES (?, ?, ?, CURRENT_TIMESTAMP)", reportedListing, OTHER, reason);
        }

        feedPost = post("Modtest feed post", SELLER, null);
        jdbc.update("INSERT INTO app_group (name, type, created_by_email, created_at) "
                + "VALUES ('Modtest study group', 'GENERAL', ?, CURRENT_TIMESTAMP)", SELLER);
        group = id("SELECT id FROM app_group WHERE name = 'Modtest study group'");
        jdbc.update("INSERT INTO group_membership (group_id, user_email, joined_at) VALUES (?, ?, CURRENT_TIMESTAMP)", group, OTHER);
        groupPost = post("Modtest post inside the group", OTHER, group);
        jdbc.update("INSERT INTO post_comment (post_id, author_email, content, created_at) "
                + "VALUES (?, ?, 'Modtest rude comment', CURRENT_TIMESTAMP)", feedPost, OTHER);
        comment = id("SELECT id FROM post_comment WHERE content = 'Modtest rude comment'");
        jdbc.update("INSERT INTO post_like (post_id, user_email, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)", feedPost, OTHER);

        jdbc.update("INSERT INTO event (title, starts_at, created_by_email, created_at) "
                + "VALUES ('Modtest party last year', TIMESTAMP WITH TIME ZONE '2025-01-01 00:00:00+00', ?, CURRENT_TIMESTAMP)", SELLER);
        pastEvent = id("SELECT id FROM event WHERE title = 'Modtest party last year'");

        jdbc.update("INSERT INTO anonymous_request (requester_email, category, description, status, created_at) "
                + "VALUES (?, 'FOOD_PANTRY', 'Modtest request', 'OPEN', CURRENT_TIMESTAMP)", SELLER);
        request = id("SELECT id FROM anonymous_request WHERE description = 'Modtest request'");
    }

    @AfterEach
    void clear() {
        for (String email : List.of(SELLER, OTHER)) {
            jdbc.update("DELETE FROM listing WHERE seller_email = ?", email);
            jdbc.update("DELETE FROM post_comment WHERE author_email = ?", email);
            jdbc.update("DELETE FROM post WHERE author_email = ?", email);
            jdbc.update("DELETE FROM app_group WHERE created_by_email = ?", email);
            jdbc.update("DELETE FROM event WHERE created_by_email = ?", email);
            jdbc.update("DELETE FROM anonymous_request WHERE requester_email = ?", email);
        }
        jdbc.update("DELETE FROM support_resource WHERE name LIKE 'Modtest%'");
        jdbc.update("DELETE FROM app_user WHERE email IN (?, ?)", ADMIN_EMAIL, SELLER);
    }

    private long listing(String title, String seller) {
        jdbc.update("INSERT INTO listing (seller_email, title, description, category, listing_type, status, "
                + "created_at, updated_at) VALUES (?, ?, 'Modtest description', 'EVENT_TICKETS', 'SELL', "
                + "'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", seller, title);
        return id("SELECT id FROM listing WHERE title = '" + title + "'");
    }

    private long post(String content, String author, Long groupId) {
        jdbc.update("INSERT INTO post (author_email, content, group_id, pinned, created_at) "
                + "VALUES (?, ?, ?, false, CURRENT_TIMESTAMP)", author, content, groupId);
        return id("SELECT id FROM post WHERE content = '" + content + "'");
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private int count(String table, String where, Object... args) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + where, Integer.class, args);
    }

    private static Jwt token(String subject, String email) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(subject).claim("email", email)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    private static Jwt admin() {
        return token("user_modTestAdmin", ADMIN_EMAIL);
    }

    private static Jwt student() {
        return token("user_modTestSeller", SELLER);
    }

    private static AdminSupportResourceRequest resource(Long schoolId, String name) {
        return new AdminSupportResourceRequest(schoolId, SupportCategory.COUNSELING, name,
                "  Walk-in hours on weekdays.  ", "", "123 Campus Way");
    }

    // ── Who may do this ──────────────────────────────────────────────────────────

    @Test
    void aStudentIsRefusedEveryModerationAction_evenOnTheirOwnContent_andNothingChanges() {
        List<Executable> everything = Arrays.asList(
                () -> service.listings(student(), "", false, 0, 20),
                () -> service.listingReports(student(), reportedListing),
                () -> service.deleteListing(student(), reportedListing),
                () -> service.dismissListingReports(student(), reportedListing),
                () -> service.posts(student(), "", 0, 20),
                () -> service.deletePost(student(), feedPost),
                () -> service.comments(student(), feedPost),
                () -> service.deleteComment(student(), comment),
                () -> service.groups(student(), "", 0, 20),
                () -> service.deleteGroup(student(), group),
                () -> service.events(student(), "", 0, 20),
                () -> service.deleteEvent(student(), pastEvent),
                () -> service.supportRequests(student(), null, 0, 20),
                () -> service.setRequestStatus(student(), request, RequestStatus.CLOSED),
                () -> service.deleteSupportRequest(student(), request),
                () -> service.createResource(student(), resource(1L, "Modtest resource")),
                () -> service.updateResource(student(), 1L, resource(1L, "Modtest resource")),
                () -> service.deleteResource(student(), 1L),
                // An id that does not exist is still 403: the role is checked first.
                () -> service.deleteListing(student(), 999_999L));

        for (Executable action : everything) {
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, action).getStatusCode());
        }
        assertEquals(2, count("listing", "title LIKE 'Modtest%'"));
        assertEquals(2, count("listing_report", "listing_id = ?", reportedListing));
        assertEquals(2, count("post", "content LIKE 'Modtest%'"));
        assertEquals(1, count("post_comment", "id = ?", comment));
        assertEquals(1, count("app_group", "id = ?", group));
        assertEquals(1, count("event", "id = ?", pastEvent));
        assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM anonymous_request WHERE id = ?", String.class, request));
        assertEquals(0, count("support_resource", "name LIKE 'Modtest%'"));
    }

    // ── Marketplace ──────────────────────────────────────────────────────────────

    @Test
    void listingsCanBeFoundByTitleOrSeller_withTheirReportCountAndFirstPhoto() {
        List<AdminListingResponse> byTitle = service.listings(admin(), "MODTEST COUNTERFEIT", false, 0, 20).items();
        assertEquals(1, byTitle.size());
        assertEquals(SELLER, byTitle.get(0).sellerEmail());
        assertEquals(2, byTitle.get(0).reportCount());
        assertEquals("https://example.com/modtest-1.png", byTitle.get(0).photoUrl());

        AdminListingResponse clean = service.listings(admin(), OTHER, false, 0, 20).items().get(0);
        assertEquals("Modtest calculus textbook", clean.title());
        assertEquals(0, clean.reportCount());
        assertNull(clean.photoUrl());
    }

    @Test
    void theReportedFilterIsTheModerationQueue() {
        List<String> queue = service.listings(admin(), "modtest", true, 0, 20).items().stream()
                .map(AdminListingResponse::title).toList();

        assertEquals(List.of("Modtest counterfeit tickets"), queue);
        assertEquals(List.of("Modtest: fake tickets", "Modtest: looks like a scam"),
                service.listingReports(admin(), reportedListing).stream().map(r -> r.reason()).sorted().toList());
    }

    @Test
    void dismissingReportsKeepsTheListing_andEmptiesItFromTheQueue() {
        service.dismissListingReports(admin(), reportedListing);

        assertEquals(1, count("listing", "id = ?", reportedListing));
        assertEquals(0, count("listing_report", "listing_id = ?", reportedListing));
        assertTrue(service.listings(admin(), "modtest", true, 0, 20).items().isEmpty());
    }

    @Test
    void deletingAListingTakesItsPhotosFavouritesAndReportsWithIt_andNothingElse() {
        service.deleteListing(admin(), reportedListing);

        assertEquals(0, count("listing", "id = ?", reportedListing));
        assertEquals(0, count("listing_photo", "listing_id = ?", reportedListing));
        assertEquals(0, count("listing_favorite", "listing_id = ?", reportedListing));
        assertEquals(0, count("listing_report", "listing_id = ?", reportedListing));
        assertEquals(1, count("listing", "id = ?", cleanListing));
        // Already gone: said plainly, so a second administrator's click is not a silent success.
        assertThrows(SearchNotFoundException.class, () -> service.deleteListing(admin(), reportedListing));
        assertThrows(SearchNotFoundException.class, () -> service.listingReports(admin(), reportedListing));
    }

    // ── Community ────────────────────────────────────────────────────────────────

    @Test
    void postsFromTheFeedAndFromGroupsAreListedTogether_withWhereTheyWerePosted() {
        List<AdminPostResponse> found = service.posts(admin(), "modtest", 0, 20).items();

        AdminPostResponse inFeed = found.stream().filter(post -> post.id() == feedPost).findFirst().orElseThrow();
        AdminPostResponse inGroup = found.stream().filter(post -> post.id() == groupPost).findFirst().orElseThrow();
        assertNull(inFeed.groupName());
        assertEquals(1, inFeed.commentCount());
        assertEquals(1, inFeed.likeCount());
        assertEquals("Modtest study group", inGroup.groupName());
        assertEquals(OTHER, inGroup.authorEmail());
        // ...and by author.
        assertEquals(1, service.posts(admin(), SELLER, 0, 20).items().size());
    }

    @Test
    void oneCommentCanBeRemovedWithoutTouchingThePost() {
        assertEquals("Modtest rude comment", service.comments(admin(), feedPost).get(0).content());

        service.deleteComment(admin(), comment);

        assertTrue(service.comments(admin(), feedPost).isEmpty());
        assertEquals(1, count("post", "id = ?", feedPost));
        assertThrows(SearchNotFoundException.class, () -> service.deleteComment(admin(), comment));
    }

    @Test
    void deletingAPostTakesItsCommentsAndLikesWithIt() {
        service.deletePost(admin(), feedPost);

        assertEquals(0, count("post", "id = ?", feedPost));
        assertEquals(0, count("post_comment", "post_id = ?", feedPost));
        assertEquals(0, count("post_like", "post_id = ?", feedPost));
        assertEquals(1, count("post", "id = ?", groupPost));
        assertThrows(SearchNotFoundException.class, () -> service.comments(admin(), feedPost));
    }

    @Test
    void aGroupShowsHowMuchDeletingItWouldRemove_andDeletingItRemovesThat() {
        AdminGroupResponse found = service.groups(admin(), "modtest study", 0, 20).items().get(0);
        assertEquals(1, found.memberCount());
        assertEquals(1, found.postCount());

        service.deleteGroup(admin(), group);

        assertEquals(0, count("app_group", "id = ?", group));
        assertEquals(0, count("group_membership", "group_id = ?", group));
        assertEquals(0, count("post", "id = ?", groupPost));
        assertEquals(1, count("post", "id = ?", feedPost), "a post outside the group is untouched");
    }

    @Test
    void pastEventsAreListedToo_andCanBeDeleted() {
        // Students only ever see upcoming events; an administrator may need to remove an old one.
        assertEquals("Modtest party last year", service.events(admin(), "modtest party", 0, 20).items().get(0).title());

        service.deleteEvent(admin(), pastEvent);

        assertTrue(service.events(admin(), "modtest party", 0, 20).items().isEmpty());
    }

    // ── Support ──────────────────────────────────────────────────────────────────

    @Test
    void supportRequestsCanBeFilteredClosedReopenedAndDeleted() {
        assertTrue(service.supportRequests(admin(), RequestStatus.OPEN, 0, 50).items().stream()
                .anyMatch(found -> found.id() == request));

        AdminSupportRequestResponse closed = service.setRequestStatus(admin(), request, RequestStatus.CLOSED);
        assertEquals("CLOSED", closed.status());
        assertFalse(service.supportRequests(admin(), RequestStatus.OPEN, 0, 50).items().stream()
                .anyMatch(found -> found.id() == request));
        assertEquals("OPEN", service.setRequestStatus(admin(), request, RequestStatus.OPEN).status());

        service.deleteSupportRequest(admin(), request);
        assertEquals(0, count("anonymous_request", "id = ?", request));
        assertThrows(SearchNotFoundException.class, () -> service.setRequestStatus(admin(), request, RequestStatus.CLOSED));
    }

    @Test
    void aRequestNeverSaysWhoAskedForHelp() {
        // The record has no field for it, so no code path can leak one by accident.
        assertTrue(Arrays.stream(AdminSupportRequestResponse.class.getRecordComponents())
                .noneMatch(field -> field.getName().toLowerCase().contains("email")
                        || field.getName().toLowerCase().contains("requester")));
    }

    @Test
    void aSupportResourceCanBeAddedCorrectedAndRemoved_andStudentsSeeEachChange() {
        Long school = jdbc.queryForObject("SELECT min(id) FROM university", Long.class);

        SupportResourceResponse created = service.createResource(admin(), resource(school, "  Modtest counselling  "));
        assertEquals("Modtest counselling", created.getName());
        assertEquals("Walk-in hours on weekdays.", created.getDescription());
        assertNull(created.getContactInfo(), "a blank optional field is stored as nothing");
        assertTrue(studentSupport.listResources(school).stream().anyMatch(r -> r.getId().equals(created.getId())));

        jdbc.update("UPDATE support_resource SET latitude = 39.13, longitude = -84.51 WHERE id = ?", created.getId());
        SupportResourceResponse updated = service.updateResource(admin(), created.getId(), resource(school, "Modtest counselling centre"));
        assertEquals("Modtest counselling centre", updated.getName());
        assertEquals(39.13, updated.getLatitude(), "the map position is not lost by an edit");

        service.deleteResource(admin(), created.getId());
        assertTrue(studentSupport.listResources(school).stream().noneMatch(r -> r.getId().equals(created.getId())));
        assertThrows(SearchNotFoundException.class, () -> service.deleteResource(admin(), created.getId()));
    }

    @Test
    void aResourceForASchoolThatDoesNotExist_is400_andNothingIsSaved() {
        ResponseStatusException refused = assertThrows(ResponseStatusException.class,
                () -> service.createResource(admin(), resource(999_999L, "Modtest nowhere")));

        assertEquals(HttpStatus.BAD_REQUEST, refused.getStatusCode());
        assertEquals(0, count("support_resource", "name LIKE 'Modtest%'"));
    }
}
