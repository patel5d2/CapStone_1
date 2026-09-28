package com.jonathansoriano.enterprisedevgroupproject.support;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * {@code requesterEmail} is kept so the requester can see their own submissions,
 * but it is never included in the public-facing DTO: that is what makes the
 * request anonymous to other students.
 */
@Entity
@Table(name = "anonymous_request")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnonymousRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String requesterEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportCategory category;

    @Column(nullable = false, length = 2000)
    private String description;

    private Long schoolId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RequestStatus status = RequestStatus.OPEN;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (status == null) {
            status = RequestStatus.OPEN;
        }
    }
}
