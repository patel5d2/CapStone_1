package com.jonathansoriano.enterprisedevgroupproject.messages;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "blocked_user",
        uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_email", "blocked_email"}))
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BlockedUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_email", nullable = false)
    private String blockerEmail;

    @Column(name = "blocked_email", nullable = false)
    private String blockedEmail;

    /** Clerk subjects of both sides; null where that side has not been identified (ADR-012). */
    @Column(name = "blocker_subject")
    private String blockerSubject;

    @Column(name = "blocked_subject")
    private String blockedSubject;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
