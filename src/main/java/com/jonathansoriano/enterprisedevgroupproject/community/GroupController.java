package com.jonathansoriano.enterprisedevgroupproject.community;

import com.jonathansoriano.enterprisedevgroupproject.community.dto.GroupRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.GroupResponse;
import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
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

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public ResponseEntity<List<GroupResponse>> search(
            @RequestParam(required = false) GroupType type,
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) String relatedValue,
            @AuthenticationPrincipal Jwt clerkSession) {
        String requesterEmail = clerkSession == null ? null : clerkSession.getClaimAsString("email");
        return ResponseEntity.ok(groupService.search(type, schoolId, relatedValue, requesterEmail));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<GroupResponse>> myGroups(@AuthenticationPrincipal Jwt clerkSession) {
        return ResponseEntity.ok(groupService.myGroups(CurrentUser.emailOf(clerkSession)));
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody GroupRequest request,
                                                 @AuthenticationPrincipal Jwt clerkSession) {
        GroupResponse created = groupService.create(request, CurrentUser.emailOf(clerkSession));
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<Void> join(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        groupService.join(id, CurrentUser.emailOf(clerkSession));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long id, @AuthenticationPrincipal Jwt clerkSession) {
        groupService.leave(id, CurrentUser.emailOf(clerkSession));
        return ResponseEntity.noContent().build();
    }
}
