package com.jonathansoriano.enterprisedevgroupproject.support;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnonymousRequestRepository extends JpaRepository<AnonymousRequest, Long> {

    List<AnonymousRequest> findByRequesterEmailOrderByCreatedAtDesc(String requesterEmail);

    @Query("""
            SELECT r FROM AnonymousRequest r
            WHERE (:schoolId IS NULL OR r.schoolId = :schoolId)
              AND (:status IS NULL OR r.status = :status)
            ORDER BY r.createdAt DESC
            """)
    List<AnonymousRequest> search(@Param("schoolId") Long schoolId, @Param("status") RequestStatus status);
}
