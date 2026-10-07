package com.jonathansoriano.enterprisedevgroupproject.support;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnonymousRequestRepository extends JpaRepository<AnonymousRequest, Long> {

    List<AnonymousRequest> findByRequesterSubjectOrderByCreatedAtDesc(String requesterSubject);

    @Query("""
            SELECT r FROM AnonymousRequest r
            WHERE (:schoolId IS NULL OR r.schoolId = :schoolId)
              AND (:status IS NULL OR r.status = :status)
            ORDER BY r.createdAt DESC
            """)
    List<AnonymousRequest> search(@Param("schoolId") Long schoolId, @Param("status") RequestStatus status);

    /** Binds requests made under the caller's verified address to their subject (ADR-012). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE anonymous_request SET requester_subject = :subject
            WHERE requester_subject IS NULL AND lower(requester_email) = lower(:email)
            """)
    int claim(@Param("email") String email, @Param("subject") String subject);
}
