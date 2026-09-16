package com.jonathansoriano.enterprisedevgroupproject.community;

import com.jonathansoriano.enterprisedevgroupproject.community.dto.GroupRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.GroupResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;

    public GroupService(GroupRepository groupRepository, GroupMembershipRepository membershipRepository) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
    }

    public List<GroupResponse> search(GroupType type, Long schoolId, String relatedValue, String requesterEmail) {
        return groupRepository.search(type, schoolId, relatedValue).stream()
                .map(group -> toResponse(group, requesterEmail))
                .collect(Collectors.toList());
    }

    public List<GroupResponse> myGroups(String userEmail) {
        return membershipRepository.findByUserEmail(userEmail).stream()
                .map(membership -> groupRepository.findById(membership.getGroupId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(group -> toResponse(group, userEmail))
                .collect(Collectors.toList());
    }

    public GroupResponse create(GroupRequest request, String creatorEmail) {
        Group group = groupRepository.save(Group.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .relatedValue(request.getRelatedValue())
                .schoolId(request.getSchoolId())
                .createdByEmail(creatorEmail)
                .build());
        membershipRepository.save(GroupMembership.builder().groupId(group.getId()).userEmail(creatorEmail).build());
        return toResponse(group, creatorEmail);
    }

    public void join(Long groupId, String userEmail) {
        findOrThrow(groupId);
        if (membershipRepository.findByGroupIdAndUserEmail(groupId, userEmail).isEmpty()) {
            membershipRepository.save(GroupMembership.builder().groupId(groupId).userEmail(userEmail).build());
        }
    }

    @Transactional
    public void leave(Long groupId, String userEmail) {
        membershipRepository.deleteByGroupIdAndUserEmail(groupId, userEmail);
    }

    private Group findOrThrow(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found: " + id));
    }

    private GroupResponse toResponse(Group group, String requesterEmail) {
        boolean joined = requesterEmail != null
                && membershipRepository.findByGroupIdAndUserEmail(group.getId(), requesterEmail).isPresent();
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
