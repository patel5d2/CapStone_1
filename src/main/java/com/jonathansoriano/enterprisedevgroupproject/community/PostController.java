package com.jonathansoriano.enterprisedevgroupproject.community;

import com.jonathansoriano.enterprisedevgroupproject.community.dto.CommentRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.CommentResponse;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.PostRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.PostResponse;
import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/community/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public ResponseEntity<List<PostResponse>> list(@RequestParam(required = false) Long groupId,
                                                     @AuthenticationPrincipal Jwt clerkSession) {
        String requesterEmail = clerkSession == null ? null : clerkSession.getClaimAsString("email");
        return ResponseEntity.ok(postService.list(groupId, requesterEmail));
    }

    @PostMapping
    public ResponseEntity<PostResponse> create(@Valid @RequestBody PostRequest request,
                                                @AuthenticationPrincipal Jwt clerkSession) {
        PostResponse created = postService.create(request, CurrentUser.emailOf(clerkSession));
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        postService.delete(id, CurrentUser.emailOf(clerkSession));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pin")
    public ResponseEntity<Void> togglePin(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        postService.togglePin(id, CurrentUser.emailOf(clerkSession));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<Void> like(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        postService.like(id, CurrentUser.emailOf(clerkSession));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlike(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        postService.unlike(id, CurrentUser.emailOf(clerkSession));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<CommentResponse>> listComments(@PathVariable Long id) {
        return ResponseEntity.ok(postService.listComments(id));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> comment(@PathVariable Long id, @Valid @RequestBody CommentRequest request,
                                                     @AuthenticationPrincipal Jwt clerkSession) {
        CommentResponse created = postService.comment(id, request, CurrentUser.emailOf(clerkSession));
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }
}
