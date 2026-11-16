package com.jonathansoriano.enterprisedevgroupproject.admin;

import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminCommentResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminEventResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminGroupResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminListingResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminPageResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminPostResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminReportResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminSupportRequestResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminSupportResourceRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.Comment;
import com.jonathansoriano.enterprisedevgroupproject.community.CommentRepository;
import com.jonathansoriano.enterprisedevgroupproject.community.Event;
import com.jonathansoriano.enterprisedevgroupproject.community.EventRepository;
import com.jonathansoriano.enterprisedevgroupproject.community.Group;
import com.jonathansoriano.enterprisedevgroupproject.community.GroupMembershipRepository;
import com.jonathansoriano.enterprisedevgroupproject.community.GroupRepository;
import com.jonathansoriano.enterprisedevgroupproject.community.Post;
import com.jonathansoriano.enterprisedevgroupproject.community.PostLikeRepository;
import com.jonathansoriano.enterprisedevgroupproject.community.PostRepository;
import com.jonathansoriano.enterprisedevgroupproject.exception.SearchNotFoundException;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.Listing;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.ListingReportRepository;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.ListingRepository;
import com.jonathansoriano.enterprisedevgroupproject.school.School;
import com.jonathansoriano.enterprisedevgroupproject.school.SchoolRepository;
import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
import com.jonathansoriano.enterprisedevgroupproject.support.AnonymousRequest;
import com.jonathansoriano.enterprisedevgroupproject.support.AnonymousRequestRepository;
import com.jonathansoriano.enterprisedevgroupproject.support.RequestStatus;
import com.jonathansoriano.enterprisedevgroupproject.support.SupportResource;
import com.jonathansoriano.enterprisedevgroupproject.support.SupportResourceRepository;
import com.jonathansoriano.enterprisedevgroupproject.support.dto.SupportResourceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * What an administrator can do to what students post (#113): see all of it in Marketplace,
 * Community and Support, and take down anything that should not be there.
 *
 * <p>Every method begins with {@link AdminAccess#require}, exactly as account management
 * does. The student-facing services keep their own rule unchanged (only the owner may edit
 * or delete); nothing there was loosened to make room for this. An administrator never
 * edits a student's words, either: a listing or a post is left alone or removed, so nothing
 * ever appears under a student's name that they did not write.
 *
 * <p>Deleting something that is already gone answers 404, which the SPA treats as "someone
 * dealt with it" and refreshes. Each removal is logged with the administrator's subject and
 * the row id.
 */
@Slf4j
@Service
public class AdminModerationService {

    private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final AdminAccess access;
    private final ListingRepository listings;
    private final ListingReportRepository listingReports;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final PostLikeRepository likes;
    private final GroupRepository groups;
    private final GroupMembershipRepository memberships;
    private final EventRepository events;
    private final AnonymousRequestRepository requests;
    private final SupportResourceRepository resources;
    private final SchoolRepository schools;

    public AdminModerationService(AdminAccess access, ListingRepository listings,
                                  ListingReportRepository listingReports, PostRepository posts,
                                  CommentRepository comments, PostLikeRepository likes, GroupRepository groups,
                                  GroupMembershipRepository memberships, EventRepository events,
                                  AnonymousRequestRepository requests, SupportResourceRepository resources,
                                  SchoolRepository schools) {
        this.access = access;
        this.listings = listings;
        this.listingReports = listingReports;
        this.posts = posts;
        this.comments = comments;
        this.likes = likes;
        this.groups = groups;
        this.memberships = memberships;
        this.events = events;
        this.requests = requests;
        this.resources = resources;
        this.schools = schools;
    }

    // ── Marketplace ──────────────────────────────────────────────────────────────

    /**
     * Listings in every status, newest first. {@code reportedOnly} narrows the list to the
     * ones a student has reported: the moderation queue (objective 9).
     *
     * <p>Transactional so the mapping can read {@code photoUrls}, which is lazy.
     */
    @Transactional(readOnly = true)
    public AdminPageResponse<AdminListingResponse> listings(Jwt clerkSession, String query, boolean reportedOnly,
                                                            int page, int size) {
        access.require(clerkSession);
        Page<Listing> found = listings.adminSearch(AdminAccountService.patternFor(query), reportedOnly,
                AdminAccountService.pageRequest(page, size, NEWEST));

        List<Long> ids = found.getContent().stream().map(Listing::getId).toList();
        Map<Long, Long> reportCounts = ids.isEmpty() ? Map.of() : listingReports.countByListing(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        Map<Long, String> schoolNames = schoolNames();

        return AdminPageResponse.of(found, found.getContent().stream()
                .map(listing -> new AdminListingResponse(listing.getId(), listing.getTitle(), listing.getDescription(),
                        listing.getCategory().name(), listing.getListingType().name(), listing.getStatus().name(),
                        listing.getPrice(), listing.getSellerEmail(), schoolNames.get(listing.getSchoolId()),
                        listing.getPhotoUrls().isEmpty() ? null : listing.getPhotoUrls().get(0),
                        reportCounts.getOrDefault(listing.getId(), 0L), listing.getCreatedAt()))
                .toList());
    }

    /** Why a listing was reported, newest first. */
    public List<AdminReportResponse> listingReports(Jwt clerkSession, long listingId) {
        access.require(clerkSession);
        findListing(listingId);
        return listingReports.findByListingIdOrderByCreatedAtDesc(listingId).stream()
                .map(report -> new AdminReportResponse(report.getId(), report.getReason(),
                        report.getReporterEmail(), report.getCreatedAt()))
                .toList();
    }

    /**
     * Takes a listing down. Its photos, favourites and reports go with it; a conversation
     * that started from it stays, no longer pointing at a listing.
     */
    @Transactional
    public void deleteListing(Jwt clerkSession, long listingId) {
        access.require(clerkSession);
        listings.delete(findListing(listingId));
        logRemoval(clerkSession, "listing", listingId);
    }

    /** The reports were looked at and the listing is fine: clear them and keep the listing. */
    @Transactional
    public void dismissListingReports(Jwt clerkSession, long listingId) {
        access.require(clerkSession);
        findListing(listingId);
        long dismissed = listingReports.deleteByListingId(listingId);
        log.info("Administrator {} dismissed {} report(s) on listing {}.", CurrentUser.subjectOf(clerkSession),
                dismissed, listingId);
    }

    // ── Community ────────────────────────────────────────────────────────────────

    /** Posts from the main feed and from every group, newest first. */
    public AdminPageResponse<AdminPostResponse> posts(Jwt clerkSession, String query, int page, int size) {
        access.require(clerkSession);
        Page<Post> found = posts.adminSearch(AdminAccountService.patternFor(query),
                AdminAccountService.pageRequest(page, size, NEWEST));

        List<Long> groupIds = found.getContent().stream().map(Post::getGroupId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, String> groupNames = groups.findAllById(groupIds).stream()
                .collect(Collectors.toMap(Group::getId, Group::getName));

        return AdminPageResponse.of(found, found.getContent().stream()
                .map(post -> new AdminPostResponse(post.getId(), post.getContent(), post.getImageUrl(),
                        post.getAuthorEmail(), post.getGroupId(), groupNames.get(post.getGroupId()),
                        comments.countByPostId(post.getId()), likes.countByPostId(post.getId()),
                        post.getCreatedAt()))
                .toList());
    }

    /** Removes a post, and with it the comments and likes under it. */
    @Transactional
    public void deletePost(Jwt clerkSession, long postId) {
        access.require(clerkSession);
        posts.delete(posts.findById(postId).orElseThrow(() -> gone("post")));
        logRemoval(clerkSession, "post", postId);
    }

    /** The comments under one post, oldest first, with who wrote each. */
    public List<AdminCommentResponse> comments(Jwt clerkSession, long postId) {
        access.require(clerkSession);
        if (!posts.existsById(postId)) {
            throw gone("post");
        }
        return comments.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(comment -> new AdminCommentResponse(comment.getId(), comment.getPostId(), comment.getContent(),
                        comment.getAuthorEmail(), comment.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void deleteComment(Jwt clerkSession, long commentId) {
        access.require(clerkSession);
        Comment comment = comments.findById(commentId).orElseThrow(() -> gone("comment"));
        comments.delete(comment);
        logRemoval(clerkSession, "comment", commentId);
    }

    public AdminPageResponse<AdminGroupResponse> groups(Jwt clerkSession, String query, int page, int size) {
        access.require(clerkSession);
        Page<Group> found = groups.adminSearch(AdminAccountService.patternFor(query),
                AdminAccountService.pageRequest(page, size, NEWEST));
        return AdminPageResponse.of(found, found.getContent().stream()
                .map(group -> new AdminGroupResponse(group.getId(), group.getName(), group.getDescription(),
                        group.getType().name(), group.getCreatedByEmail(),
                        memberships.countByGroupId(group.getId()), posts.countByGroupId(group.getId()),
                        group.getCreatedAt()))
                .toList());
    }

    /**
     * Removes a group and everything in it: its memberships and every post made there,
     * whoever wrote them. That reach is why the list shows the post count first.
     */
    @Transactional
    public void deleteGroup(Jwt clerkSession, long groupId) {
        access.require(clerkSession);
        groups.delete(groups.findById(groupId).orElseThrow(() -> gone("group")));
        logRemoval(clerkSession, "group", groupId);
    }

    /** Every event, past ones included, most recent start first. */
    public AdminPageResponse<AdminEventResponse> events(Jwt clerkSession, String query, int page, int size) {
        access.require(clerkSession);
        Page<Event> found = events.adminSearch(AdminAccountService.patternFor(query),
                AdminAccountService.pageRequest(page, size, Sort.by(Sort.Direction.DESC, "startsAt", "id")));
        Map<Long, String> schoolNames = schoolNames();
        return AdminPageResponse.of(found, found.getContent().stream()
                .map(event -> new AdminEventResponse(event.getId(), event.getTitle(), event.getDescription(),
                        event.getLocation(), event.getStartsAt(), schoolNames.get(event.getSchoolId()),
                        event.getCreatedByEmail(), event.getCreatedAt()))
                .toList());
    }

    @Transactional
    public void deleteEvent(Jwt clerkSession, long eventId) {
        access.require(clerkSession);
        events.delete(events.findById(eventId).orElseThrow(() -> gone("event")));
        logRemoval(clerkSession, "event", eventId);
    }

    // ── Support ──────────────────────────────────────────────────────────────────

    /** Requests for help in every status (or one), newest first, without who asked. */
    public AdminPageResponse<AdminSupportRequestResponse> supportRequests(Jwt clerkSession, RequestStatus status,
                                                                          int page, int size) {
        access.require(clerkSession);
        Page<AnonymousRequest> found = requests.adminSearch(status,
                AdminAccountService.pageRequest(page, size, NEWEST));
        Map<Long, String> schoolNames = schoolNames();
        return AdminPageResponse.of(found, found.getContent().stream()
                .map(request -> toResponse(request, schoolNames))
                .toList());
    }

    /** Reopens, fulfils or closes a request. A student can only fulfil one. */
    @Transactional
    public AdminSupportRequestResponse setRequestStatus(Jwt clerkSession, long requestId, RequestStatus status) {
        access.require(clerkSession);
        AnonymousRequest request = requests.findById(requestId).orElseThrow(() -> gone("request"));
        request.setStatus(status);
        log.info("Administrator {} marked support request {} {}.", CurrentUser.subjectOf(clerkSession),
                requestId, status);
        return toResponse(requests.save(request), schoolNames());
    }

    @Transactional
    public void deleteSupportRequest(Jwt clerkSession, long requestId) {
        access.require(clerkSession);
        requests.delete(requests.findById(requestId).orElseThrow(() -> gone("request")));
        logRemoval(clerkSession, "support request", requestId);
    }

    /** Adds a pantry, aid office or counselling service to a school's Support tab. */
    @Transactional
    public SupportResourceResponse createResource(Jwt clerkSession, AdminSupportResourceRequest request) {
        access.require(clerkSession);
        requireSchool(request.schoolId());
        SupportResource saved = resources.save(apply(new SupportResource(), request));
        log.info("Administrator {} added support resource {}.", CurrentUser.subjectOf(clerkSession), saved.getId());
        return toResponse(saved);
    }

    /** Corrects a resource. Its map position is left as it was: the form has no fields for it. */
    @Transactional
    public SupportResourceResponse updateResource(Jwt clerkSession, long resourceId,
                                                  AdminSupportResourceRequest request) {
        access.require(clerkSession);
        SupportResource resource = resources.findById(resourceId).orElseThrow(() -> gone("support resource"));
        requireSchool(request.schoolId());
        SupportResource saved = resources.save(apply(resource, request));
        log.info("Administrator {} updated support resource {}.", CurrentUser.subjectOf(clerkSession), resourceId);
        return toResponse(saved);
    }

    @Transactional
    public void deleteResource(Jwt clerkSession, long resourceId) {
        access.require(clerkSession);
        resources.delete(resources.findById(resourceId).orElseThrow(() -> gone("support resource")));
        logRemoval(clerkSession, "support resource", resourceId);
    }

    // ── Shared ───────────────────────────────────────────────────────────────────

    private Listing findListing(long listingId) {
        return listings.findById(listingId).orElseThrow(() -> gone("listing"));
    }

    private void requireSchool(Long schoolId) {
        if (schools.findById(schoolId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an available school.");
        }
    }

    private static SupportResource apply(SupportResource resource, AdminSupportResourceRequest request) {
        resource.setSchoolId(request.schoolId());
        resource.setCategory(request.category());
        resource.setName(request.name().trim());
        resource.setDescription(blankToNull(request.description()));
        resource.setContactInfo(blankToNull(request.contactInfo()));
        resource.setAddress(blankToNull(request.address()));
        return resource;
    }

    private static SupportResourceResponse toResponse(SupportResource resource) {
        return SupportResourceResponse.builder().id(resource.getId()).schoolId(resource.getSchoolId())
                .category(resource.getCategory()).name(resource.getName()).description(resource.getDescription())
                .contactInfo(resource.getContactInfo()).address(resource.getAddress())
                .latitude(resource.getLatitude()).longitude(resource.getLongitude()).build();
    }

    private static AdminSupportRequestResponse toResponse(AnonymousRequest request, Map<Long, String> schoolNames) {
        return new AdminSupportRequestResponse(request.getId(), request.getCategory().name(),
                request.getDescription(), request.getStatus().name(), schoolNames.get(request.getSchoolId()),
                request.getCreatedAt());
    }

    private Map<Long, String> schoolNames() {
        return schools.findAll().stream().collect(Collectors.toMap(School::getId, School::getName));
    }

    private static SearchNotFoundException gone(String what) {
        return new SearchNotFoundException("That " + what + " no longer exists.");
    }

    private static void logRemoval(Jwt clerkSession, String what, long id) {
        log.info("Administrator {} removed {} {}.", CurrentUser.subjectOf(clerkSession), what, id);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
