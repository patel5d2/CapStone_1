package com.jonathansoriano.enterprisedevgroupproject.support;

import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
import com.jonathansoriano.enterprisedevgroupproject.support.dto.AnonymousRequestRequest;
import com.jonathansoriano.enterprisedevgroupproject.support.dto.AnonymousRequestResponse;
import com.jonathansoriano.enterprisedevgroupproject.support.dto.SupportResourceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/support")
public class SupportController {

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping("/resources")
    public ResponseEntity<List<SupportResourceResponse>> resources(@RequestParam(required = false) Long schoolId) {
        return ResponseEntity.ok(supportService.listResources(schoolId));
    }

    @GetMapping("/requests")
    public ResponseEntity<List<AnonymousRequestResponse>> publicRequests(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) RequestStatus status) {
        return ResponseEntity.ok(supportService.listPublicRequests(schoolId, status));
    }

    @GetMapping("/requests/mine")
    public ResponseEntity<List<AnonymousRequestResponse>> myRequests(@AuthenticationPrincipal Jwt clerkSession) {
        return ResponseEntity.ok(supportService.listMine(CurrentUser.emailOf(clerkSession)));
    }

    @PostMapping("/requests")
    public ResponseEntity<AnonymousRequestResponse> createRequest(@Valid @RequestBody AnonymousRequestRequest request,
                                                                    @AuthenticationPrincipal Jwt clerkSession) {
        AnonymousRequestResponse created = supportService.createRequest(request, CurrentUser.emailOf(clerkSession));
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PostMapping("/requests/{id}/fulfill")
    public ResponseEntity<Void> fulfill(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        // Any signed-in student may fulfill an anonymous request: fulfillment is done
        // by a donor, not necessarily the original (anonymous) requester.
        CurrentUser.emailOf(clerkSession);
        supportService.fulfill(id);
        return ResponseEntity.noContent().build();
    }
}
