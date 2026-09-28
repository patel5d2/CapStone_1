package com.jonathansoriano.enterprisedevgroupproject.support.dto;

import com.jonathansoriano.enterprisedevgroupproject.support.SupportCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SupportResourceResponse {
    private Long id;
    private Long schoolId;
    private SupportCategory category;
    private String name;
    private String description;
    private String contactInfo;
    private String address;
    private Double latitude;
    private Double longitude;
}
