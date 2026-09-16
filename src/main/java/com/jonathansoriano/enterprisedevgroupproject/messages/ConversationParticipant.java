package com.jonathansoriano.enterprisedevgroupproject.messages;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "conversation_participant",
        uniqueConstraints = @UniqueConstraint(columnNames = {"conversation_id", "user_email"}))
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Builder.Default
    private int unreadCount = 0;

    private Instant lastReadAt;
}
