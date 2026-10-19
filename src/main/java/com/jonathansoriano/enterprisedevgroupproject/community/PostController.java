package com.jonathansoriano.enterprisedevgroupproject.community;

import com.jonathansoriano.enterprisedevgroupproject.community.dto.CommentRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.CommentResponse;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.PostRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.PostResponse;
import com.jonathansoriano.enterprisedevgroupproject.identity.CallerIdentity;
import com.jonathansoriano.enterprisedevgroupproject.identity.Party;
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
    private final CallerIdentity identity;

    public PostController(PostService postService, CallerIdentity identity) {
        this.postService = postService;
        this.identity = identity;
    }

    @GetMapping
    public ResponseEntity<List<PostResponse>> list(@RequestParam(required = false) Long groupId,
                                                     @AuthenticationPrincipal Jwt clerkSession) {
        // Identity comes from the verified token only (invariant 1); no session, no viewer.
        Party viewer = clerkSession == null ? null : identity.caller(clerkSession);
        return ResponseEntity.ok(postService.list(groupId, viewer));
    }

    @PostMapping
    public ResponseEntity<PostResponse> create(@Valid @RequestBody PostRequest request,
                                                @AuthenticationPrincipal Jwt clerkSession) {
        PostResponse created = postService.create(request, identity.caller(clerkSession));
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        postService.delete(id, identity.caller(clerkSession));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pin")
    public ResponseEntity<Void> togglePin(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        postService.togglePin(id, identity.caller(clerkSession));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<Void> like(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        postService.like(id, identity.caller(clerkSession));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlike(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        postService.unlike(id, identity.caller(clerkSession));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<CommentResponse>> listComments(@PathVariable Long id) {
        return ResponseEntity.ok(postService.listComments(id));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> comment(@PathVariable Long id, @Valid @RequestBody CommentRequest request,
                                                     @AuthenticationPrincipal Jwt clerkSession) {
        CommentResponse created = postService.comment(id, request, identity.caller(clerkSession));
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }
}
