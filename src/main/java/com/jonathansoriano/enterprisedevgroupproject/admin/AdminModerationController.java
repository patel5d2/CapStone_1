package com.jonathansoriano.enterprisedevgroupproject.admin;

import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminCommentResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminEventResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminGroupResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminListingResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminPageResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminPostResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminReportResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminRequestStatusRequest;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminSupportRequestResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminSupportResourceRequest;
import com.jonathansoriano.enterprisedevgroupproject.support.RequestStatus;
import com.jonathansoriano.enterprisedevgroupproject.support.dto.SupportResourceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Moderation endpoints for Marketplace, Community and Support (#113). As with account
 * management, the administrator check is in {@link AdminModerationService}; the
 * {@code /api/admin} prefix protects nothing by itself.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminModerationController extends AdminEndpoints {

    private final AdminModerationService service;

    public AdminModerationController(AdminModerationService service) {
        this.service = service;
    }

    // ── Marketplace ──

    @GetMapping("/listings")
    public AdminPageResponse<AdminListingResponse> listings(@AuthenticationPrincipal Jwt clerkSession,
                                                            @RequestParam(defaultValue = "") String query,
                                                            @RequestParam(defaultValue = "false") boolean reportedOnly,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return service.listings(clerkSession, query, reportedOnly, page, size);
    }

    @GetMapping("/listings/{id}/reports")
    public List<AdminReportResponse> listingReports(@AuthenticationPrincipal Jwt clerkSession, @PathVariable long id) {
        return service.listingReports(clerkSession, id);
    }

    @DeleteMapping("/listings/{id}")
    public ResponseEntity<Void> deleteListing(@AuthenticationPrincipal Jwt clerkSession, @PathVariable long id) {
        service.deleteListing(clerkSession, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/listings/{id}/reports")
    public ResponseEntity<Void> dismissListingReports(@AuthenticationPrincipal Jwt clerkSession,
                                                      @PathVariable long id) {
        service.dismissListingReports(clerkSession, id);
        return ResponseEntity.noContent().build();
    }

    // ── Community ──

    @GetMapping("/posts")
    public AdminPageResponse<AdminPostResponse> posts(@AuthenticationPrincipal Jwt clerkSession,
                                                      @RequestParam(defaultValue = "") String query,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return service.posts(clerkSession, query, page, size);
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(@AuthenticationPrincipal Jwt clerkSession, @PathVariable long id) {
        service.deletePost(clerkSession, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/posts/{id}/comments")
    public List<AdminCommentResponse> comments(@AuthenticationPrincipal Jwt clerkSession, @PathVariable long id) {
        return service.comments(clerkSession, id);
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(@AuthenticationPrincipal Jwt clerkSession, @PathVariable long id) {
        service.deleteComment(clerkSession, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/groups")
    public AdminPageResponse<AdminGroupResponse> groups(@AuthenticationPrincipal Jwt clerkSession,
                                                        @RequestParam(defaultValue = "") String query,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return service.groups(clerkSession, query, page, size);
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> deleteGroup(@AuthenticationPrincipal Jwt clerkSession, @PathVariable long id) {
        service.deleteGroup(clerkSession, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/events")
    public AdminPageResponse<AdminEventResponse> events(@AuthenticationPrincipal Jwt clerkSession,
                                                        @RequestParam(defaultValue = "") String query,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return service.events(clerkSession, query, page, size);
    }

    @DeleteMapping("/events/{id}")
    public ResponseEntity<Void> deleteEvent(@AuthenticationPrincipal Jwt clerkSession, @PathVariable long id) {
        service.deleteEvent(clerkSession, id);
        return ResponseEntity.noContent().build();
    }

    // ── Support ──

    @GetMapping("/support/requests")
    public AdminPageResponse<AdminSupportRequestResponse> supportRequests(
            @AuthenticationPrincipal Jwt clerkSession,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.supportRequests(clerkSession, status, page, size);
    }

    @PutMapping("/support/requests/{id}/status")
    public AdminSupportRequestResponse setRequestStatus(@AuthenticationPrincipal Jwt clerkSession,
                                                        @PathVariable long id,
                                                        @Valid @RequestBody AdminRequestStatusRequest request) {
        return service.setRequestStatus(clerkSession, id, request.status());
    }

    @DeleteMapping("/support/requests/{id}")
    public ResponseEntity<Void> deleteSupportRequest(@AuthenticationPrincipal Jwt clerkSession,
                                                     @PathVariable long id) {
        service.deleteSupportRequest(clerkSession, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/support/resources")
    public ResponseEntity<SupportResourceResponse> createResource(
            @AuthenticationPrincipal Jwt clerkSession, @Valid @RequestBody AdminSupportResourceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createResource(clerkSession, request));
    }

    @PutMapping("/support/resources/{id}")
    public SupportResourceResponse updateResource(@AuthenticationPrincipal Jwt clerkSession, @PathVariable long id,
                                                  @Valid @RequestBody AdminSupportResourceRequest request) {
        return service.updateResource(clerkSession, id, request);
    }

    @DeleteMapping("/support/resources/{id}")
    public ResponseEntity<Void> deleteResource(@AuthenticationPrincipal Jwt clerkSession, @PathVariable long id) {
        service.deleteResource(clerkSession, id);
        return ResponseEntity.noContent().build();
    }
}
