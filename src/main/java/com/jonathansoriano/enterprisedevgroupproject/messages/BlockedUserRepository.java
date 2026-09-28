package com.jonathansoriano.enterprisedevgroupproject.messages;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {
    Optional<BlockedUser> findByBlockerEmailAndBlockedEmail(String blockerEmail, String blockedEmail);
    List<BlockedUser> findByBlockerEmail(String blockerEmail);
    void deleteByBlockerEmailAndBlockedEmail(String blockerEmail, String blockedEmail);
    boolean existsByBlockerEmailAndBlockedEmail(String blockerEmail, String blockedEmail);
}
