package com.jonathansoriano.enterprisedevgroupproject.marketplace.dto;

import com.jonathansoriano.enterprisedevgroupproject.marketplace.ListingCategory;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.ListingStatus;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.ListingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ListingResponse {
    private Long id;
    private String sellerEmail;
    private String title;
    private String description;
    private ListingCategory category;
    private ListingType listingType;
    private ListingStatus status;
    private BigDecimal price;
    private String courseCode;
    private Long schoolId;
    private List<String> photoUrls;
    private boolean favorited;
    private Instant createdAt;
    private Instant updatedAt;
}
