package com.jonathansoriano.enterprisedevgroupproject.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, Long> {
    boolean existsByGroupIdAndUserSubject(Long groupId, String userSubject);
    List<GroupMembership> findByUserSubject(String userSubject);
    long countByGroupId(Long groupId);
    void deleteByGroupIdAndUserSubject(Long groupId, String userSubject);

    /**
     * Binds the caller's subject to memberships still keyed only on their verified address.
     * At most one row per group, and none in a group the subject already belongs to, so
     * (group_id, user_subject) stays unique within the statement as well as after it.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE group_membership SET user_subject = :subject
            WHERE user_subject IS NULL AND lower(user_email) = lower(:email)
              AND id = (SELECT min(c.id) FROM group_membership c
                        WHERE c.group_id = group_membership.group_id
                          AND c.user_subject IS NULL AND lower(c.user_email) = lower(:email))
              AND NOT EXISTS (SELECT 1 FROM group_membership other
                              WHERE other.group_id = group_membership.group_id
                                AND other.user_subject = :subject)
            """)
    int claim(@Param("email") String email, @Param("subject") String subject);
}
