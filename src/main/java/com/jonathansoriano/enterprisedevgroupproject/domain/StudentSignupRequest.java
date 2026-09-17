package com.jonathansoriano.enterprisedevgroupproject.domain;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
//This is the class that will be used to create a new Student in the student table.
public class StudentSignupRequest {
    @NotBlank(message = "First name field is required")
    @Size(max = 100, message = "First name must be 100 characters or fewer")
    private String firstName;

    @NotBlank(message = "Last name field is required")
    @Size(max = 100, message = "Last name must be 100 characters or fewer")
    private String lastName;

    @NotBlank(message = "Resident City field is required")
    @Size(max = 100, message = "City must be 100 characters or fewer")
    private String residentCity;

    @NotBlank(message = "Resident State field is required")
    @Size(min = 2, max = 2, message = "Resident state must be a Capitalized 2-letter code")
    @Pattern(regexp = "^[A-Z]{2}$", message = "Resident state field must contain 2 capitalized letters")
    private String residentState;

    @NotNull(message = "University ID is required")
    private Integer universityId;

    @NotBlank(message = "Grade field is required")
    @Size(max = 20, message = "Grade must be 20 characters or fewer")
    private String grade;

    @NotBlank(message = "Major field is required")
    @Size(max = 255, message = "Major must be 255 characters or fewer")
    private String major;

    // Set server-side from the caller's Clerk session token; a value sent by the
    // client is overwritten before this request reaches the service layer.
    @NotBlank(message = "Email field is required")
    @Size(max = 255, message = "Email must be 255 characters or fewer")
    @Email(message = "Please provide a valid email address")
    @Pattern(
            regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
            message = "Email format is invalid"
    )
    private String email;

    @Size(max = 255, message = "Link must be 255 characters or fewer")
    private String socialMediaLink;
}
