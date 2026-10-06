package com.jonathansoriano.enterprisedevgroupproject.messages;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {

    /** Candidate blocks made by a party; the caller narrows them with {@link Party#is}. */
    @Query("select b from BlockedUser b where b.blockerSubject = :subject or lower(b.blockerEmail) = lower(:email)")
    List<BlockedUser> findCandidatesByBlocker(@Param("subject") String subject, @Param("email") String email);

    /**
     * Binds the caller's subject to blocks they made while known only by address. Where the
     * other side is already bound, only one row per (caller, blocked subject) is bound, so the
     * (blocker_subject, blocked_subject) unique index holds within the statement; any leftover
     * duplicate keeps matching through its address.
     */
    // ponytail: lower() scans blocked_user per messaging request; expression indexes if it grows.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE blocked_user SET blocker_subject = :subject
            WHERE blocker_subject IS NULL AND lower(blocker_email) = lower(:email)
              AND (blocked_subject IS NULL
                   OR id = (SELECT min(c.id) FROM blocked_user c
                            WHERE c.blocker_subject IS NULL AND lower(c.blocker_email) = lower(:email)
                              AND c.blocked_subject = blocked_user.blocked_subject))
              AND NOT EXISTS (SELECT 1 FROM blocked_user other
                              WHERE other.blocker_subject = :subject
                                AND other.blocked_subject = blocked_user.blocked_subject)
            """)
    int claimAsBlocker(@Param("email") String email, @Param("subject") String subject);

    /**
     * Binds the caller's subject to blocks placed on their address, so a block keeps holding
     * after they change it. Same one-row-per-pair rule as {@link #claimAsBlocker}.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE blocked_user SET blocked_subject = :subject
            WHERE blocked_subject IS NULL AND lower(blocked_email) = lower(:email)
              AND (blocker_subject IS NULL
                   OR id = (SELECT min(c.id) FROM blocked_user c
                            WHERE c.blocked_subject IS NULL AND lower(c.blocked_email) = lower(:email)
                              AND c.blocker_subject = blocked_user.blocker_subject))
              AND NOT EXISTS (SELECT 1 FROM blocked_user other
                              WHERE other.blocked_subject = :subject
                                AND other.blocker_subject = blocked_user.blocker_subject)
            """)
    int claimAsBlocked(@Param("email") String email, @Param("subject") String subject);
}
