package com.jonathansoriano.enterprisedevgroupproject.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByGroupIdIsNullOrderByPinnedDescCreatedAtDesc();
    List<Post> findByGroupIdOrderByPinnedDescCreatedAtDesc(Long groupId);
}
