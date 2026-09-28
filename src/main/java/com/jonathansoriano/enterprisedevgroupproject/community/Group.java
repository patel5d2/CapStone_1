package com.jonathansoriano.enterprisedevgroupproject.community;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One engine backs major groups, graduation-year groups, and course study
 * groups: {@code relatedValue} holds whichever of those the {@code type} calls
 * for (a major name, a graduation year, or a course code).
 */
@Entity
@Table(name = "app_group")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupType type;

    private String relatedValue;

    /** Null means the group is open across all Cincinnati-area schools. */
    private Long schoolId;

    @Column(nullable = false)
    private String createdByEmail;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
