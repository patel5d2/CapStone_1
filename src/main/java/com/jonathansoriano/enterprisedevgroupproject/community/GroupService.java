package com.jonathansoriano.enterprisedevgroupproject.community;

import com.jonathansoriano.enterprisedevgroupproject.community.dto.GroupRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.GroupResponse;
import com.jonathansoriano.enterprisedevgroupproject.identity.Party;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/** Memberships are the caller's own rows, found by Clerk subject (ADR-012). */
@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;

    public GroupService(GroupRepository groupRepository, GroupMembershipRepository membershipRepository) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
    }

    /** {@code viewer} may be null (no session): then nothing shows as joined. */
    public List<GroupResponse> search(GroupType type, Long schoolId, String relatedValue, Party viewer) {
        return groupRepository.search(type, schoolId, relatedValue).stream()
                .map(group -> toResponse(group, viewer))
                .collect(Collectors.toList());
    }

    public List<GroupResponse> myGroups(Party caller) {
        return membershipRepository.findByUserSubject(caller.subject()).stream()
                .map(membership -> groupRepository.findById(membership.getGroupId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(group -> toResponse(group, caller))
                .collect(Collectors.toList());
    }

    public GroupResponse create(GroupRequest request, Party creator) {
        Group group = groupRepository.save(Group.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .relatedValue(request.getRelatedValue())
                .schoolId(request.getSchoolId())
                .createdByEmail(creator.email())
                .createdBySubject(creator.subject())
                .build());
        membershipRepository.save(membership(group.getId(), creator));
        return toResponse(group, creator);
    }

    public void join(Long groupId, Party caller) {
        findOrThrow(groupId);
        if (!membershipRepository.existsByGroupIdAndUserSubject(groupId, caller.subject())) {
            membershipRepository.save(membership(groupId, caller));
        }
    }

    @Transactional
    public void leave(Long groupId, Party caller) {
        membershipRepository.deleteByGroupIdAndUserSubject(groupId, caller.subject());
    }

    private static GroupMembership membership(Long groupId, Party member) {
        return GroupMembership.builder().groupId(groupId).userEmail(member.email()).userSubject(member.subject()).build();
    }

    private Group findOrThrow(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found: " + id));
    }

    private GroupResponse toResponse(Group group, Party viewer) {
        boolean joined = viewer != null
                && membershipRepository.existsByGroupIdAndUserSubject(group.getId(), viewer.subject());
        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .type(group.getType())
                .relatedValue(group.getRelatedValue())
                .schoolId(group.getSchoolId())
                .createdByEmail(group.getCreatedByEmail())
                .memberCount(membershipRepository.countByGroupId(group.getId()))
                .joined(joined)
                .createdAt(group.getCreatedAt())
                .build();
    }
}
