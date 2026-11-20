package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

import java.time.Instant;

public record AdminEventResponse(Long id, String title, String description, String location, Instant startsAt,
                                 String schoolName, String createdByEmail, Instant createdAt) {
}
