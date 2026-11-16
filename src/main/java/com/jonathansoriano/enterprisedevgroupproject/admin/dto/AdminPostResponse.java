package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

import java.time.Instant;

/** @param groupName null for a post on the main feed, or when its group is gone */
public record AdminPostResponse(Long id, String content, String imageUrl, String authorEmail, Long groupId,
                                String groupName, long commentCount, long likeCount, Instant createdAt) {
}
