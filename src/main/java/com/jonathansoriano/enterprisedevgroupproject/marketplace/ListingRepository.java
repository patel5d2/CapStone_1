package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    List<Listing> findBySellerEmailOrderByCreatedAtDesc(String sellerEmail);

    @Query("""
            SELECT l FROM Listing l
            WHERE (:category IS NULL OR l.category = :category)
              AND (:listingType IS NULL OR l.listingType = :listingType)
              AND (:status IS NULL OR l.status = :status)
              AND (:schoolId IS NULL OR l.schoolId = :schoolId)
              AND (:courseCode IS NULL OR LOWER(l.courseCode) = LOWER(:courseCode))
              AND (:keyword IS NULL
                   OR LOWER(l.title) LIKE CONCAT('%', LOWER(:keyword), '%')
                   OR LOWER(l.description) LIKE CONCAT('%', LOWER(:keyword), '%'))
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
