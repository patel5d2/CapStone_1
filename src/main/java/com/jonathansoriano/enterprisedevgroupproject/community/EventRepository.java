package com.jonathansoriano.enterprisedevgroupproject.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("""
            SELECT e FROM Event e
            WHERE (:schoolId IS NULL OR e.schoolId = :schoolId OR e.schoolId IS NULL)
              AND e.startsAt >= :after
            ORDER BY e.startsAt ASC
            """)
    List<Event> findUpcoming(@Param("schoolId") Long schoolId, @Param("after") Instant after);

    /** Binds events created under the caller's verified address to their subject (ADR-012). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE event SET created_by_subject = :subject
            WHERE created_by_subject IS NULL AND lower(created_by_email) = lower(:email)
            """)
    int claim(@Param("email") String email, @Param("subject") String subject);
}
