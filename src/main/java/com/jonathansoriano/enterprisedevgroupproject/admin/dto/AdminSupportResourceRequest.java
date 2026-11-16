package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

import com.jonathansoriano.enterprisedevgroupproject.support.SupportCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A campus support resource, as created or edited by an administrator. Bounds mirror the columns. */
public record AdminSupportResourceRequest(
        @NotNull(message = "School is required") Long schoolId,
        @NotNull(message = "Category is required") SupportCategory category,
        @NotBlank(message = "Name is required") @Size(max = 255, message = "Name must be 255 characters or fewer") String name,
        @Size(max = 2000, message = "Description must be 2000 characters or fewer") String description,
        @Size(max = 255, message = "Contact details must be 255 characters or fewer") String contactInfo,
        @Size(max = 255, message = "Address must be 255 characters or fewer") String address) {
}
