package com.jonathansoriano.enterprisedevgroupproject.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** Binds groups created under the caller's verified address to their subject (ADR-012). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE app_group SET created_by_subject = :subject
            WHERE created_by_subject IS NULL AND lower(created_by_email) = lower(:email)
            """)
    int claim(@Param("email") String email, @Param("subject") String subject);
}
