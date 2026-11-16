package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

import java.time.Instant;

/** One student's report about another, as filed from Messages. */
public record AdminUserReportResponse(Long id, String reportedEmail, String reporterEmail, String reason,
                                      Instant createdAt) {
}
