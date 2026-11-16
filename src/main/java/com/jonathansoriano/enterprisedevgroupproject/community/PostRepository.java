package com.jonathansoriano.enterprisedevgroupproject.community;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** Every post, in any group or none, whose text or author matches; for an administrator (#113). */
    @Query("""
            SELECT p FROM Post p
            WHERE LOWER(p.content) LIKE :pattern ESCAPE '!' OR LOWER(p.authorEmail) LIKE :pattern ESCAPE '!'
            """)
    Page<Post> adminSearch(@Param("pattern") String pattern, Pageable pageable);

    long countByGroupId(Long groupId);

    List<Post> findByGroupIdIsNullOrderByPinnedDescCreatedAtDesc();
    List<Post> findByGroupIdOrderByPinnedDescCreatedAtDesc(Long groupId);

    /** Binds posts written under the caller's verified address to their subject (ADR-012). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE post SET author_subject = :subject
            WHERE author_subject IS NULL AND lower(author_email) = lower(:email)
            """)
    int claim(@Param("email") String email, @Param("subject") String subject);
}
