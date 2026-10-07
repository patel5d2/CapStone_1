package com.jonathansoriano.enterprisedevgroupproject.support;

import com.jonathansoriano.enterprisedevgroupproject.identity.CallerIdentity;
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
    private final CallerIdentity identity;

    public SupportController(SupportService supportService, CallerIdentity identity) {
        this.supportService = supportService;
        this.identity = identity;
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
        return ResponseEntity.ok(supportService.listMine(identity.caller(clerkSession)));
    }

    @PostMapping("/requests")
    public ResponseEntity<AnonymousRequestResponse> createRequest(@Valid @RequestBody AnonymousRequestRequest request,
                                                                    @AuthenticationPrincipal Jwt clerkSession) {
        AnonymousRequestResponse created = supportService.createRequest(request, identity.caller(clerkSession));
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PostMapping("/requests/{id}/fulfill")
    public ResponseEntity<Void> fulfill(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        // Any signed-in student may fulfill an anonymous request: fulfillment is done
        // by a donor, not necessarily the original (anonymous) requester.
        identity.caller(clerkSession);
        supportService.fulfill(id);
        return ResponseEntity.noContent().build();
    }
}
