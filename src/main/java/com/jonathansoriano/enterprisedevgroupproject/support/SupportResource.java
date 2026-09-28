package com.jonathansoriano.enterprisedevgroupproject.support;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One school's food pantry, emergency aid office, counseling center, etc. Shown
 * on the Support tab and pinned on the campus map.
 */
@Entity
@Table(name = "support_resource")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SupportResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long schoolId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportCategory category;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    private String contactInfo;

    private String address;

    private Double latitude;

    private Double longitude;
}
