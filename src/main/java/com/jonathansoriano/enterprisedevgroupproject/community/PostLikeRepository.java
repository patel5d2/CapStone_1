package com.jonathansoriano.enterprisedevgroupproject.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    Optional<PostLike> findByPostIdAndUserEmail(Long postId, String userEmail);
    long countByPostId(Long postId);
    void deleteByPostIdAndUserEmail(Long postId, String userEmail);
}
