package com.jonathansoriano.enterprisedevgroupproject.marketplace.dto;

import com.jonathansoriano.enterprisedevgroupproject.marketplace.ListingCategory;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.ListingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ListingRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Category is required")
    private ListingCategory category;

    @NotNull(message = "Listing type is required")
    private ListingType listingType;

    private BigDecimal price;

    private String courseCode;

    private Long schoolId;

    private List<String> photoUrls;
}
