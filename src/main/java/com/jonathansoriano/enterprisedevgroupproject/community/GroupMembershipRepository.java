package com.jonathansoriano.enterprisedevgroupproject.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, Long> {
    Optional<GroupMembership> findByGroupIdAndUserEmail(Long groupId, String userEmail);
    List<GroupMembership> findByUserEmail(String userEmail);
    List<GroupMembership> findByGroupId(Long groupId);
    long countByGroupId(Long groupId);
    void deleteByGroupIdAndUserEmail(Long groupId, String userEmail);
}
