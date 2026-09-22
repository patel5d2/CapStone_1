package com.jonathansoriano.enterprisedevgroupproject.profile;

import com.jonathansoriano.enterprisedevgroupproject.messages.MessagingService;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.StartConversationRequest;
import com.jonathansoriano.enterprisedevgroupproject.service.StudentIdentityService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Directory contact never sends another student's email to the browser. */
@RestController
@RequestMapping("/api/students")
public class DirectoryContactController {
    private final ProfileRecordRepository records;
    private final MessagingService messages;
    private final StudentIdentityService identity;
    public DirectoryContactController(ProfileRecordRepository records, MessagingService messages,
            StudentIdentityService identity) {
        this.records = records;
        this.messages = messages;
        this.identity = identity;
    }
    public record ContactResponse(Long id) {}
    @PostMapping("/{studentId}/conversation")
    public ContactResponse contact(@PathVariable Long studentId, @AuthenticationPrincipal Jwt jwt) {
        var recipient = records.findById(studentId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Student not found."));
        var conversation = messages.startConversation(StartConversationRequest.builder()
                .recipientEmail(recipient.getEmail()).build(), identity.ownerEmailFor(jwt));
        return new ContactResponse(conversation.getId());
    }
}
