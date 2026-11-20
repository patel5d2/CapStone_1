package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

import com.jonathansoriano.enterprisedevgroupproject.domain.ProfileFieldBounds;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The account fields an administrator may change. The bounds are the ones a student's own
 * edit carries ({@code EditStudentDetailsRequest}), because they mirror the same columns.
 *
 * <p>What is absent is absent on purpose, and anything else sent in the body is ignored:
 * <ul>
 *   <li><b>email</b> is the Clerk identity the row hangs off, changed only in Clerk;</li>
 *   <li><b>photo</b> is an upload its owner made, tracked by {@code image_asset};</li>
 *   <li><b>visibility</b> is the student's own choice (invariant 5);</li>
 *   <li><b>role</b> is never granted through a request ({@code AdminAccess}).</li>
 * </ul>
 * The school <em>is</em> here although a student cannot change their own: correcting a wrong
 * school is exactly the kind of fix that needs an administrator.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminStudentUpdateRequest {
    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must be 100 characters or fewer")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must be 100 characters or fewer")
    private String lastName;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must be 100 characters or fewer")
    private String residentCity;

    @NotBlank(message = "State is required")
    @Pattern(regexp = "^[A-Z]{2}$", message = "State must be a capitalized 2-letter code")
    private String residentState;

    @NotNull(message = "School is required")
    private Long universityId;

    @NotBlank(message = "Year of study is required")
    @Size(max = 20, message = "Year of study must be 20 characters or fewer")
    private String grade;

    @NotBlank(message = "Major is required")
    @Size(max = 255, message = "Major must be 255 characters or fewer")
    private String major;

    @Size(max = 255, message = "Link must be 255 characters or fewer")
    private String socialMediaLink;

    @Min(value = ProfileFieldBounds.GRADUATION_YEAR_MIN, message = "Graduation year must be a four-digit year")
    @Max(value = ProfileFieldBounds.GRADUATION_YEAR_MAX, message = "Graduation year must be a four-digit year")
    private Integer graduationYear;

    @Size(max = ProfileFieldBounds.BIO_MAX,
            message = "Bio must be " + ProfileFieldBounds.BIO_MAX + " characters or fewer")
    private String bio;
}
