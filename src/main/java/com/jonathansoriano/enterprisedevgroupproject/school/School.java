package com.jonathansoriano.enterprisedevgroupproject.school;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A Cincinnati-area school, backed by the existing {@code university} table
 * (the student directory's name for the same concept). Read-only here: rows are
 * managed by the directory's own signup flow.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class School {
    private Long id;
    private String name;
}
