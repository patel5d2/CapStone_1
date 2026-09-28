package com.jonathansoriano.enterprisedevgroupproject.messages;

import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/block")
public class BlockController {

    private final MessagingService messagingService;

    public BlockController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @GetMapping
    public ResponseEntity<List<String>> listBlocked(@AuthenticationPrincipal Jwt clerkSession) {
        return ResponseEntity.ok(messagingService.listBlocked(CurrentUser.emailOf(clerkSession)));
    }

    @PostMapping
    public ResponseEntity<Void> block(@RequestBody Map<String, String> body, @AuthenticationPrincipal Jwt clerkSession) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        messagingService.block(CurrentUser.emailOf(clerkSession), email);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{email}")
    public ResponseEntity<Void> unblock(@PathVariable String email, @AuthenticationPrincipal Jwt clerkSession) {
        messagingService.unblock(CurrentUser.emailOf(clerkSession), email);
        return ResponseEntity.noContent().build();
    }
}
