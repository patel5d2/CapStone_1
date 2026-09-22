package com.jonathansoriano.enterprisedevgroupproject.profile;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/** Read-only projection of the legacy student table; writes remain in StudentService. */
@Entity
@Table(name = "student")
@Immutable
@Getter
public class ProfileRecord {
    @Id private Long id;
    private String email;
    private String clerkUserId;
    private String firstName;
    private String lastName;
    private Integer graduationYear;
    private String bio;
    private String photoUrl;
    private Long universityId;
}
