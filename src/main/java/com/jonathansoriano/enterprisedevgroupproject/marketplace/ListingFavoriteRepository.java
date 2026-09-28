package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ListingFavoriteRepository extends JpaRepository<ListingFavorite, Long> {
    Optional<ListingFavorite> findByListingIdAndUserEmail(Long listingId, String userEmail);
    List<ListingFavorite> findByUserEmailOrderByCreatedAtDesc(String userEmail);
    void deleteByListingIdAndUserEmail(Long listingId, String userEmail);
}
