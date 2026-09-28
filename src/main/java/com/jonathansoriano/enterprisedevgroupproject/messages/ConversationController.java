package com.jonathansoriano.enterprisedevgroupproject.messages;

import com.jonathansoriano.enterprisedevgroupproject.messages.dto.ConversationResponse;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.MessageResponse;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.SendMessageRequest;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.StartConversationRequest;
import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages/conversations")
public class ConversationController {

    private final MessagingService messagingService;

    public ConversationController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> list(@AuthenticationPrincipal Jwt clerkSession) {
        return ResponseEntity.ok(messagingService.listConversations(CurrentUser.emailOf(clerkSession)));
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> start(@RequestBody StartConversationRequest request,
                                                        @AuthenticationPrincipal Jwt clerkSession) {
        ConversationResponse response = messagingService.startConversation(request, CurrentUser.emailOf(clerkSession));
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<MessageResponse>> listMessages(@PathVariable Long id,
                                                                @AuthenticationPrincipal Jwt clerkSession) {
        return ResponseEntity.ok(messagingService.listMessages(id, CurrentUser.emailOf(clerkSession)));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> sendMessage(@PathVariable Long id, @Valid @RequestBody SendMessageRequest request,
                                                         @AuthenticationPrincipal Jwt clerkSession) {
        MessageResponse response = messagingService.sendMessage(id, request, CurrentUser.emailOf(clerkSession));
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        messagingService.markRead(id, CurrentUser.emailOf(clerkSession));
        return ResponseEntity.noContent().build();
    }
}
