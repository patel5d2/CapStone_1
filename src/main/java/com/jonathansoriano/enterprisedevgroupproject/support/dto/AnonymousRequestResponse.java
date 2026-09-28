package com.jonathansoriano.enterprisedevgroupproject.support.dto;

import com.jonathansoriano.enterprisedevgroupproject.support.RequestStatus;
import com.jonathansoriano.enterprisedevgroupproject.support.SupportCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Deliberately has no requester field: this is what other students and the
 * public list see, so a request stays anonymous even to people fulfilling it.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnonymousRequestResponse {
    private Long id;
    private SupportCategory category;
    private String description;
    private Long schoolId;
    private RequestStatus status;
    private Instant createdAt;
}
