package com.jonathansoriano.enterprisedevgroupproject.community;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
