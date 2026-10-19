package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    List<Listing> findBySellerSubjectOrderByCreatedAtDesc(String sellerSubject);

    /** Binds the caller's subject to listings they posted while known only by address. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE listing SET seller_subject = :subject
            WHERE seller_subject IS NULL AND lower(seller_email) = lower(:email)
            """)
    int claim(@Param("email") String email, @Param("subject") String subject);

    @Query("""
            SELECT l FROM Listing l
            WHERE (:category IS NULL OR l.category = :category)
              AND (:listingType IS NULL OR l.listingType = :listingType)
              AND (:status IS NULL OR l.status = :status)
              AND (:schoolId IS NULL OR l.schoolId = :schoolId)
              AND (:courseCode IS NULL OR LOWER(l.courseCode) = LOWER(CAST(:courseCode AS string)))
              AND (:keyword IS NULL
                   OR LOWER(l.title) LIKE CONCAT('%', LOWER(CAST(:keyword AS string)), '%')
                   OR LOWER(l.description) LIKE CONCAT('%', LOWER(CAST(:keyword AS string)), '%'))
            ORDER BY l.createdAt DESC
            """)
    List<Listing> search(
            @Param("category") ListingCategory category,
            @Param("listingType") ListingType listingType,
            @Param("status") ListingStatus status,
            @Param("schoolId") Long schoolId,
            @Param("courseCode") String courseCode,
            @Param("keyword") String keyword
    );
}
