package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "listing_favorite", uniqueConstraints = @UniqueConstraint(columnNames = {"listing_id", "user_email"}))
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ListingFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
