package com.jonathansoriano.enterprisedevgroupproject.community.dto;

import com.jonathansoriano.enterprisedevgroupproject.community.GroupType;
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
public class GroupRequest {
    @NotBlank(message = "Name is required")
    private String name;
    private String description;
    @NotNull(message = "Type is required")
    private GroupType type;
    private String relatedValue;
    private Long schoolId;
}
