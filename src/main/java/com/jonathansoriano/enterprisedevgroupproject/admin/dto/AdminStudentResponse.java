package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

/**
 * One student account as an administrator sees it.
 *
 * <p>Carries the email address and every profile field regardless of the student's
 * visibility choices: an administrator has to be able to tell two accounts apart before
 * editing or deleting one. It is returned only by {@code /api/admin} after the administrator
 * check, never by a student-facing endpoint, and it never includes the Clerk user id.
 *
 * @param universityName null when the row points at a school that no longer exists
 * @param hasSignIn      a Clerk account is bound to this profile; false for a directory row
 *                       nobody has signed in to (the demo data)
 * @param administrator  this address holds the administrator role, so it cannot be deleted here
 * @param banned         an administrator has banned this address; the profile is hidden from
 *                       the directory and its owner is refused by every endpoint
 */
public record AdminStudentResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        Long universityId,
        String universityName,
        String grade,
        String major,
        String residentCity,
        String residentState,
        String socialMediaLink,
        Integer graduationYear,
        String bio,
        boolean hasSignIn,
        boolean administrator,
        boolean banned) {
}
