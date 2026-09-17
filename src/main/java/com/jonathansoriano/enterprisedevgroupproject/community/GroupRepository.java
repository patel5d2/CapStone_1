package com.jonathansoriano.enterprisedevgroupproject.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {

    @Query("""
            SELECT g FROM Group g
            WHERE (:type IS NULL OR g.type = :type)
              AND (:schoolId IS NULL OR g.schoolId = :schoolId OR g.schoolId IS NULL)
              AND (:relatedValue IS NULL OR LOWER(g.relatedValue) = LOWER(CAST(:relatedValue AS string)))
            ORDER BY g.createdAt DESC
            """)
    List<Group> search(@Param("type") GroupType type, @Param("schoolId") Long schoolId, @Param("relatedValue") String relatedValue);
}
