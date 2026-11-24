package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ListingReportRepository extends JpaRepository<ListingReport, Long> {

    List<ListingReport> findByListingIdOrderByCreatedAtDesc(Long listingId);

    /** Pairs of (listing id, number of reports), for the listings on one page. */
    @Query("SELECT r.listingId, COUNT(r) FROM ListingReport r WHERE r.listingId IN :listingIds GROUP BY r.listingId")
    List<Object[]> countByListing(@Param("listingIds") Collection<Long> listingIds);

    long deleteByListingId(Long listingId);


    /** Binds the caller's subject to reports they filed while known only by address. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE listing_report SET reporter_subject = :subject
            WHERE reporter_subject IS NULL AND lower(reporter_email) = lower(:email)
            """)
    int claim(@Param("email") String email, @Param("subject") String subject);
}
