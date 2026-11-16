package com.jonathansoriano.enterprisedevgroupproject.service;

import com.jonathansoriano.enterprisedevgroupproject.dto.StudentUpdateDto;
import com.jonathansoriano.enterprisedevgroupproject.repository.StudentRepository;
import com.jonathansoriano.enterprisedevgroupproject.security.ClerkAccountLookup;
import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * ADR-012, the compatibility half: resolves who the caller is from the verified Clerk
 * subject while the database still keys ownership on email.
 *
 * <p>Sixteen ownership columns across thirteen tables hold email addresses, and Clerk
 * addresses are mutable, so a student who changes their address today loses their
 * listings, conversations, blocks and posts. The cutover of those columns is S1-10,
 * S1-11 and S1-12. Until then this is the one place that turns a token into an owner:
 * it looks up the Clerk subject first and hands back the address <em>stored</em> on that
 * row, so a changed address still finds the same data through the existing email path.
 *
 * <p><b>It never binds a subject to a row it merely matched by email.</b> The 33
 * fabricated students seeded by {@code V3} live on plausible addresses such as
 * {@code sarah.johnson@mail.uc.edu}; binding on an email match would hand a real student
 * a fabricated profile the first time one signed in. A row is bound when its owner
 * creates it, and from S1-04 by the verified {@code user.created} webhook — never here.
 * See {@code docs/phase-1/identity-backfill.md}.
 */
@Service
public class StudentIdentityService {

    /** What a caller is told when the profile on their address belongs to someone else. */
    public static final String PROFILE_CLAIMED_MESSAGE =
            "This profile is already linked to a different account. Sign in with the account "
                    + "that created it, or contact support.";

    private final StudentRepository studentRepository;
    private final ClerkAccountLookup clerkAccounts;
    private final DeletedAccountCleanup deletedAccounts;

    public StudentIdentityService(StudentRepository studentRepository, ClerkAccountLookup clerkAccounts,
                                  DeletedAccountCleanup deletedAccounts) {
        this.studentRepository = studentRepository;
        this.clerkAccounts = clerkAccounts;
        this.deletedAccounts = deletedAccounts;
    }

    /**
     * The email address this caller's data is keyed on.
     *
     * <p>Subject first: if a row is bound to the token's {@code sub}, its stored address
     * wins, even when Clerk now reports a different one. Otherwise the token's address is
     * used unchanged, which is how every legacy and not-yet-created row keeps working.
     *
     * @param clerkSession the caller's verified Clerk session token
     * @return the address to key ownership on for this request
     * @throws ResponseStatusException 401 if the token carries no subject or no email,
     *         409 if the address belongs to a row owned by a different Clerk subject
     */
    public String ownerEmailFor(Jwt clerkSession) {
        String subject = CurrentUser.subjectOf(clerkSession);
        String tokenEmail = CurrentUser.emailOf(clerkSession);

        return studentRepository.findStudentByClerkUserId(subject)
                .map(StudentUpdateDto::getEmail)
                .orElseGet(() -> {
                    refuseIfClaimedByAnother(tokenEmail, subject);
                    return tokenEmail;
                });
    }

    /**
     * Stops a second Clerk account reaching a profile through the email path after the
     * first one has claimed it — the "another subject cannot claim the data" case.
     *
     * <p>An unbound row (the common case: every legacy row) is left alone rather than
     * claimed, and a row with no match at all is nothing to protect.
     *
     * <p>Recycled addresses and re-created accounts both land here. Neither is guessed at:
     * Clerk is asked whether the account the profile is bound to still exists. If it was
     * deleted, its profile is deleted with it and the caller carries on as someone with no
     * profile yet — so a student who deletes their account and signs up again starts
     * fresh, and a new holder of a recycled address never sees the previous holder's
     * details. If the account is alive (its owner changed address), or Clerk cannot be
     * asked, the caller is refused as before and it does take a human.
     */
    private void refuseIfClaimedByAnother(String tokenEmail, String subject) {
        studentRepository.findStudentByEmail(tokenEmail)
                .map(StudentUpdateDto::getClerkUserId)
                .filter(owner -> !owner.equals(subject))
                .ifPresent(owner -> {
                    if (clerkAccounts.isDeleted(owner, subject)) {
                        deletedAccounts.retire(owner);
                        return;
                    }
                    throw new ResponseStatusException(HttpStatus.CONFLICT, PROFILE_CLAIMED_MESSAGE);
                });
    }
}
