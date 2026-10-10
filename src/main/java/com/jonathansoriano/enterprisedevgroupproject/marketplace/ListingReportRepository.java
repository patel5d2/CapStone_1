package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingReportRepository extends JpaRepository<ListingReport, Long> {

    /** Binds the caller's subject to reports they filed while known only by address. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE listing_report SET reporter_subject = :subject
            WHERE reporter_subject IS NULL AND lower(reporter_email) = lower(:email)
            """)
    int claim(@Param("email") String email, @Param("subject") String subject);
}
