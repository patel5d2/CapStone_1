package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

import java.time.Instant;

/** @param postCount posts that would be deleted along with the group */
public record AdminGroupResponse(Long id, String name, String description, String type, String createdByEmail,
                                 long memberCount, long postCount, Instant createdAt) {
}
