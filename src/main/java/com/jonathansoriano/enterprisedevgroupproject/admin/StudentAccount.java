package com.jonathansoriano.enterprisedevgroupproject.admin;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/**
 * Read-only view of the {@code student} table for account management: every column an
 * administrator may see, which is more than the directory shows other students.
 *
 * <p>Read-only on purpose, like {@code ProfileRecord}. Writes to this table stay in the two
 * places that already own them, {@code StudentRepository.updateStudent} for a profile and
 * {@code DeletedAccountCleanup} for removing one, so the rules attached to each keep applying.
 */
@Entity
@Table(name = "student")
@Immutable
@Getter
public class StudentAccount {
    @Id private Long id;
    private String firstName;
    private String lastName;
    private String residentCity;
    private String residentState;
    private Long universityId;
    private String grade;
    private String major;
    private String email;
    /** The Clerk account that owns this profile; null for a row nobody has signed in to. */
    private String clerkUserId;
    private String socialMediaLink;
    private Integer graduationYear;
    private String bio;
    private String photoUrl;
}
