package com.jonathansoriano.enterprisedevgroupproject.messages;

import com.jonathansoriano.enterprisedevgroupproject.identity.CallerIdentity;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.ConversationResponse;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.MessageResponse;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.SendMessageRequest;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.StartConversationRequest;
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
    private final CallerIdentity identity;

    public ConversationController(MessagingService messagingService, CallerIdentity identity) {
        this.messagingService = messagingService;
        this.identity = identity;
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> list(@AuthenticationPrincipal Jwt clerkSession) {
        return ResponseEntity.ok(messagingService.listConversations(identity.caller(clerkSession)));
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> start(@RequestBody StartConversationRequest request,
                                                        @AuthenticationPrincipal Jwt clerkSession) {
        ConversationResponse response = messagingService.startConversation(request, identity.caller(clerkSession));
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<MessageResponse>> listMessages(@PathVariable Long id,
                                                                @AuthenticationPrincipal Jwt clerkSession) {
        return ResponseEntity.ok(messagingService.listMessages(id, identity.caller(clerkSession)));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> sendMessage(@PathVariable Long id, @Valid @RequestBody SendMessageRequest request,
                                                         @AuthenticationPrincipal Jwt clerkSession) {
        MessageResponse response = messagingService.sendMessage(id, request, identity.caller(clerkSession));
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        messagingService.markRead(id, identity.caller(clerkSession));
        return ResponseEntity.noContent().build();
    }
}
