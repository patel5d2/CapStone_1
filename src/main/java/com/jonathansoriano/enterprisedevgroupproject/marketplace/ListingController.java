package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingRequest;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingResponse;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ReportRequest;
import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/marketplace")
public class ListingController {

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping("/listings")
    public ResponseEntity<List<ListingResponse>> search(
            @RequestParam(required = false) ListingCategory category,
            @RequestParam(required = false) ListingType listingType,
            @RequestParam(required = false) ListingStatus status,
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) String courseCode,
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal Jwt clerkSession) {
        String requesterEmail = clerkSession == null ? null : clerkSession.getClaimAsString("email");
        return ResponseEntity.ok(listingService.search(category, listingType, status, schoolId, courseCode, q, requesterEmail));
    }

    @GetMapping("/listings/{id}")
    public ResponseEntity<ListingResponse> get(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        String requesterEmail = clerkSession == null ? null : clerkSession.getClaimAsString("email");
        return ResponseEntity.ok(listingService.get(id, requesterEmail));
    }

    @GetMapping("/my-listings")
    public ResponseEntity<List<ListingResponse>> myListings(@AuthenticationPrincipal Jwt clerkSession) {
        return ResponseEntity.ok(listingService.myListings(CurrentUser.emailOf(clerkSession)));
    }

    @GetMapping("/favorites")
    public ResponseEntity<List<ListingResponse>> myFavorites(@AuthenticationPrincipal Jwt clerkSession) {
        return ResponseEntity.ok(listingService.myFavorites(CurrentUser.emailOf(clerkSession)));
    }

    @PostMapping("/listings")
    public ResponseEntity<ListingResponse> create(@Valid @RequestBody ListingRequest request,
                                                   @AuthenticationPrincipal Jwt clerkSession) {
        ListingResponse created = listingService.create(request, CurrentUser.emailOf(clerkSession));
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/listings/{id}")
    public ResponseEntity<ListingResponse> update(@PathVariable Long id, @Valid @RequestBody ListingRequest request,
                                                   @AuthenticationPrincipal Jwt clerkSession) {
        return ResponseEntity.ok(listingService.update(id, request, CurrentUser.emailOf(clerkSession)));
    }

    @DeleteMapping("/listings/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        listingService.delete(id, CurrentUser.emailOf(clerkSession));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/listings/{id}/sold")
    public ResponseEntity<ListingResponse> markSold(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        return ResponseEntity.ok(listingService.markSold(id, CurrentUser.emailOf(clerkSession)));
    }

    @PostMapping("/listings/{id}/favorite")
    public ResponseEntity<Void> favorite(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        listingService.favorite(id, CurrentUser.emailOf(clerkSession));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/listings/{id}/favorite")
    public ResponseEntity<Void> unfavorite(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        listingService.unfavorite(id, CurrentUser.emailOf(clerkSession));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/listings/{id}/report")
    public ResponseEntity<Void> report(@PathVariable Long id, @Valid @RequestBody ReportRequest request,
                                        @AuthenticationPrincipal Jwt clerkSession) {
        listingService.report(id, request, CurrentUser.emailOf(clerkSession));
        return ResponseEntity.noContent().build();
    }
}
