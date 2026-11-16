package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

import java.time.Instant;

public record AdminCommentResponse(Long id, Long postId, String content, String authorEmail, Instant createdAt) {
}
