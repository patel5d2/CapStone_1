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
public class CommentResponse {
    private Long id;
    private Long postId;
    private String authorEmail;
    private String content;
    private Instant createdAt;
}
