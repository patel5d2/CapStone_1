package com.jonathansoriano.enterprisedevgroupproject.community;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "group_membership", uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_email"}))
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GroupMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    /** Clerk subject of the member; null until they have been identified (ADR-012). */
    @Column(name = "user_subject")
    private String userSubject;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    void onCreate() {
        joinedAt = Instant.now();
    }
}
