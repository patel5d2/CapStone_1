package com.jonathansoriano.enterprisedevgroupproject.community;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "post")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String authorEmail;

    /** Null means it belongs to the general community feed rather than one group. */
    private Long groupId;

    @Column(nullable = false, length = 4000)
    private String content;

    private String imageUrl;

    @Builder.Default
    private boolean pinned = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
