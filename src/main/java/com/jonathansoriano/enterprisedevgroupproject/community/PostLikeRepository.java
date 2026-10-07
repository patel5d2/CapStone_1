package com.jonathansoriano.enterprisedevgroupproject.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    boolean existsByPostIdAndUserSubject(Long postId, String userSubject);
    long countByPostId(Long postId);
    void deleteByPostIdAndUserSubject(Long postId, String userSubject);

    /** Binds address-only likes to the caller, one per post, as {@link GroupMembershipRepository#claim} does. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE post_like SET user_subject = :subject
            WHERE user_subject IS NULL AND lower(user_email) = lower(:email)
              AND id = (SELECT min(c.id) FROM post_like c
                        WHERE c.post_id = post_like.post_id
                          AND c.user_subject IS NULL AND lower(c.user_email) = lower(:email))
              AND NOT EXISTS (SELECT 1 FROM post_like other
                              WHERE other.post_id = post_like.post_id
                                AND other.user_subject = :subject)
            """)
    int claim(@Param("email") String email, @Param("subject") String subject);
}
