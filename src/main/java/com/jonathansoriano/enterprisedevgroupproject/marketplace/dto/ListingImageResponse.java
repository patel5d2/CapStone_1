package com.jonathansoriano.enterprisedevgroupproject.marketplace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ListingImageResponse {
    private String id;
    private String photoUrl;
}
