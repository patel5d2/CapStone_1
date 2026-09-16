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
public class EventResponse {
    private Long id;
    private String title;
    private String description;
    private Instant startsAt;
    private String location;
    private Long schoolId;
    private Long listingId;
    private String createdByEmail;
    private Instant createdAt;
}
