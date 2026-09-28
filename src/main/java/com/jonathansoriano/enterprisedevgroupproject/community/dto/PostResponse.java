package com.jonathansoriano.enterprisedevgroupproject.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PostResponse {
    private Long id;
    private String authorEmail;
    private Long groupId;
    private String content;
    private String imageUrl;
    private boolean pinned;
    private long likeCount;
    private long commentCount;
    private boolean likedByMe;
    private Instant createdAt;
}
