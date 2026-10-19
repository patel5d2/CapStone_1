package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ListingFavoriteRepository extends JpaRepository<ListingFavorite, Long> {
    Optional<ListingFavorite> findByListingIdAndUserSubject(Long listingId, String userSubject);
    List<ListingFavorite> findByUserSubjectOrderByCreatedAtDesc(String userSubject);
    void deleteByListingIdAndUserSubject(Long listingId, String userSubject);

    /**
     * Binds the caller's subject to favorites still keyed only on their verified address. At
     * most one row per listing, and none on a listing the subject already favorited, so
     * (listing_id, user_subject) stays unique within the statement as well as after it.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE listing_favorite SET user_subject = :subject
            WHERE user_subject IS NULL AND lower(user_email) = lower(:email)
              AND id = (SELECT min(c.id) FROM listing_favorite c
                        WHERE c.listing_id = listing_favorite.listing_id
                          AND c.user_subject IS NULL AND lower(c.user_email) = lower(:email))
              AND NOT EXISTS (SELECT 1 FROM listing_favorite other
                              WHERE other.listing_id = listing_favorite.listing_id
                                AND other.user_subject = :subject)
            """)
    int claim(@Param("email") String email, @Param("subject") String subject);
}
