package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** One bounded page of anything an administrator lists (invariant 10). {@code page} is zero-based. */
public record AdminPageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    /** The page Spring Data found, carrying {@code items} mapped from its content. */
    public static <T> AdminPageResponse<T> of(Page<?> found, List<T> items) {
        return new AdminPageResponse<>(items, found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages());
    }
}
