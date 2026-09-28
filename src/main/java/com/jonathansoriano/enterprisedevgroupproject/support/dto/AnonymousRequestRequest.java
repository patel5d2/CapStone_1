package com.jonathansoriano.enterprisedevgroupproject.support.dto;

import com.jonathansoriano.enterprisedevgroupproject.support.SupportCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnonymousRequestRequest {
    @NotNull(message = "Category is required")
    private SupportCategory category;
    @NotBlank(message = "Description is required")
    private String description;
    private Long schoolId;
}
