package com.jonathansoriano.enterprisedevgroupproject.marketplace.dto;

import com.jonathansoriano.enterprisedevgroupproject.marketplace.ListingCategory;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.ListingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    public static final int MIN_PHOTOS = 1;
    public static final int MAX_PHOTOS = 4;

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

    @NotNull(message = "At least one photo is required")
    @Size(
            min = MIN_PHOTOS,
            max = MAX_PHOTOS,
            message = "A listing must have between 1 and 4 photos"
    )
    private List<
            @NotBlank
            @Pattern(
                    regexp = "^[0-9a-fA-F-]{36}$",
                    message = "Invalid photo ID"
            )
                    String
            > photoIds;
}
