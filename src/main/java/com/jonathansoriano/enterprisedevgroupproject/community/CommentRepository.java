package com.jonathansoriano.enterprisedevgroupproject.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);
    long countByPostId(Long postId);

    /** Binds comments written under the caller's verified address to their subject (ADR-012). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE post_comment SET author_subject = :subject
            WHERE author_subject IS NULL AND lower(author_email) = lower(:email)
            """)
    int claim(@Param("email") String email, @Param("subject") String subject);
}
