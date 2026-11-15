package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "listing_image")
@Getter
@Setter
@NoArgsConstructor
public class ListingImage {

    @Id
    @Column(length = 36, nullable = false)
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id")
    private Listing listing;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Column(name = "photo_url", nullable = false)
    private String photoUrl;

    @Column(name = "public_id")
    private String publicId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
