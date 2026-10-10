package com.jonathansoriano.enterprisedevgroupproject.community;

import com.jonathansoriano.enterprisedevgroupproject.community.dto.GroupRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.GroupResponse;
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
@RequestMapping("/api/community/groups")
public class GroupController {

    private final GroupService groupService;
    private final CallerIdentity identity;

    public GroupController(GroupService groupService, CallerIdentity identity) {
        this.groupService = groupService;
        this.identity = identity;
    }

    @GetMapping
    public ResponseEntity<List<GroupResponse>> search(
            @RequestParam(required = false) GroupType type,
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) String relatedValue,
            @AuthenticationPrincipal Jwt clerkSession) {
        // Identity comes from the verified token only (invariant 1); no session, no viewer.
        Party viewer = clerkSession == null ? null : identity.caller(clerkSession);
        return ResponseEntity.ok(groupService.search(type, schoolId, relatedValue, viewer));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<GroupResponse>> myGroups(@AuthenticationPrincipal Jwt clerkSession) {
        return ResponseEntity.ok(groupService.myGroups(identity.caller(clerkSession)));
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody GroupRequest request,
                                                 @AuthenticationPrincipal Jwt clerkSession) {
        GroupResponse created = groupService.create(request, identity.caller(clerkSession));
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<Void> join(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        groupService.join(id, identity.caller(clerkSession));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        groupService.leave(id, identity.caller(clerkSession));
        return ResponseEntity.noContent().build();
    }
}
