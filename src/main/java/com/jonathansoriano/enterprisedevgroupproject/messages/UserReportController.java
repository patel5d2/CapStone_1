package com.jonathansoriano.enterprisedevgroupproject.messages;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/users/report")
public class UserReportController {

    private final UserReportRepository userReportRepository;
    private final MessagingIdentity identity;

    public UserReportController(UserReportRepository userReportRepository, MessagingIdentity identity) {
        this.userReportRepository = userReportRepository;
        this.identity = identity;
    }

    @PostMapping
    public ResponseEntity<Void> report(@RequestBody Map<String, String> body, @AuthenticationPrincipal Jwt clerkSession) {
        String reportedEmail = body.get("email");
        String reason = body.get("reason");
        if (reportedEmail == null || reportedEmail.isBlank() || reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and reason are required");
        }
        Party reporter = identity.caller(clerkSession);
        Party reported = identity.byAddress(reportedEmail);
        userReportRepository.save(UserReport.builder()
                .reporterEmail(reporter.email())
                .reporterSubject(reporter.subject())
                .reportedEmail(reported.email())
                .reportedSubject(reported.subject())
                .reason(reason)
                .build());
        return ResponseEntity.noContent().build();
    }
}
