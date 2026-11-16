package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

import java.time.Instant;

/** One report filed against a listing. */
public record AdminReportResponse(Long id, String reason, String reporterEmail, Instant createdAt) {
}
