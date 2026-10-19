package com.jonathansoriano.enterprisedevgroupproject.messages;

import com.jonathansoriano.enterprisedevgroupproject.identity.Party;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    List<ConversationParticipant> findByConversationId(Long conversationId);

    /** Candidates for a party; the caller narrows them with {@link Party#is}. */
    @Query("select p from ConversationParticipant p where p.userSubject = :subject or lower(p.userEmail) = lower(:email)")
    List<ConversationParticipant> findCandidates(@Param("subject") String subject, @Param("email") String email);

    /**
     * Binds the caller's subject to participant rows still keyed only on the caller's
     * verified address, so their inbox follows them through a later address change. At most
     * one row per conversation, and none in a conversation the subject is already in, which
     * keeps (conversation_id, user_subject) unique within the statement as well as after it.
     * Addresses compare case-insensitively, as {@link Party#is} does.
     */
    // ponytail: lower() defeats the email indexes, so this scans the table on every messaging
    // request; add expression indexes (PostgreSQL) if messaging volume makes it measurable.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE conversation_participant SET user_subject = :subject
            WHERE user_subject IS NULL AND lower(user_email) = lower(:email)
              AND id = (SELECT min(c.id) FROM conversation_participant c
                        WHERE c.conversation_id = conversation_participant.conversation_id
                          AND c.user_subject IS NULL AND lower(c.user_email) = lower(:email))
              AND NOT EXISTS (SELECT 1 FROM conversation_participant other
                              WHERE other.conversation_id = conversation_participant.conversation_id
                                AND other.user_subject = :subject)
            """)
    int claim(@Param("email") String email, @Param("subject") String subject);
}
