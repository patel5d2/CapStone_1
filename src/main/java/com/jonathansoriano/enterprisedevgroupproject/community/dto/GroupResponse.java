package com.jonathansoriano.enterprisedevgroupproject.community.dto;

import com.jonathansoriano.enterprisedevgroupproject.community.GroupType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GroupResponse {
    private Long id;
    private String name;
    private String description;
    private GroupType type;
    private String relatedValue;
    private Long schoolId;
    private String createdByEmail;
    private long memberCount;
    private boolean joined;
    private Instant createdAt;
}
