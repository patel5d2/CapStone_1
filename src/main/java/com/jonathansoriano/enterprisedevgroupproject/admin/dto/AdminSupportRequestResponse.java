package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

import java.time.Instant;

/**
 * A request for help, as an administrator sees it.
 *
 * <p>There is deliberately no requester here. These are posted anonymously by students asking
 * for food, emergency aid or counselling, and an administrator can do everything moderation
 * needs (close it, delete it) without learning who asked.
 */
public record AdminSupportRequestResponse(Long id, String category, String description, String status,
                                          String schoolName, Instant createdAt) {
}
