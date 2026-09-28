package com.jonathansoriano.enterprisedevgroupproject.community.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PostRequest {
    /** Null posts to the general community feed. */
    private Long groupId;
    @NotBlank(message = "Content is required")
    private String content;
    private String imageUrl;
}
