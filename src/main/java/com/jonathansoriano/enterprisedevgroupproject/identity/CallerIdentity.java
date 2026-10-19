package com.jonathansoriano.enterprisedevgroupproject.identity;

import com.jonathansoriano.enterprisedevgroupproject.dto.StudentUpdateDto;
import com.jonathansoriano.enterprisedevgroupproject.repository.StudentRepository;
import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
import com.jonathansoriano.enterprisedevgroupproject.service.StudentIdentityService;
import com.jonathansoriano.enterprisedevgroupproject.webhook.ClerkIdentityRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who the caller is (ADR-012). Every controller that acts for a signed-in student resolves
 * them here, and features compare the result's subject with the subject stored on a row —
 * never an address.
 *
 * <p><b>Claiming.</b> Rows written before their owner's subject was known carry only an
 * address. Before returning the caller, each feature's {@link IdentityClaim} binds the rows
 * on the caller's <em>verified</em> addresses to the caller's subject; after that they
 * follow the caller through any address change. This differs from the student-row rule
 * ("never bind by email match", identity-backfill.md rule 1), which exists because seeded
 * demo students sit on plausible addresses; no feature data is seeded, and a row on an
 * address has always meant "whoever holds that address" — which the verified token proves.
 * Decision 018 (Proposed).
 */
@Component
public class CallerIdentity {

    private final StudentIdentityService studentIdentity;
    private final StudentRepository students;
    private final ClerkIdentityRepository clerkIdentities;
    private final List<IdentityClaim> claims;
    // ponytail: per-instance memory of subjects already claimed for the once-only features;
    // a restart claims again, which is idempotent. Move to a table if it ever matters.
    private final Set<String> claimedOnce = ConcurrentHashMap.newKeySet();

    public CallerIdentity(StudentIdentityService studentIdentity, StudentRepository students,
                          ClerkIdentityRepository clerkIdentities, List<IdentityClaim> claims) {
        this.studentIdentity = studentIdentity;
        this.students = students;
        this.clerkIdentities = clerkIdentities;
        this.claims = claims;
    }

    /**
     * The caller, from the verified token only (invariant 1), with their address-only rows
     * claimed. Their address is the token's current one. Rows are claimed on two addresses:
     * the token's, unless a different known account is recorded on it (then the rows stay
     * unbound rather than bound by a guess); and the address stored on the caller's bound
     * student row when it differs — those rows are theirs from before an address change.
     *
     * @throws org.springframework.web.server.ResponseStatusException 401 without a subject
     *         or email claim; 409 if the address belongs to a profile bound to another subject
     */
    @Transactional
    public Party caller(Jwt clerkSession) {
        String subject = CurrentUser.subjectOf(clerkSession);
        String storedEmail = studentIdentity.ownerEmailFor(clerkSession);
        Party caller = new Party(subject, CurrentUser.emailOf(clerkSession));

        boolean firstSeen = !claimedOnce.contains(subject);
        String holder = byAddress(caller.email()).subject();
        if (holder == null || holder.equals(subject)) {
            claim(caller.email(), subject, firstSeen);
        }
        if (!storedEmail.equalsIgnoreCase(caller.email())) {
            claim(storedEmail, subject, firstSeen);
        }
        // Only after the claims ran, so a failure is retried on the next request.
        claimedOnce.add(subject);
        return caller;
    }

    private void claim(String email, String subject, boolean firstSeen) {
        for (IdentityClaim claim : claims) {
            if (firstSeen || claim.everyRequest()) {
                claim.claim(email, subject);
            }
        }
    }

    /**
     * Someone named only by an address. Their subject is filled in when exactly one known
     * Clerk identity holds the address — a student row bound to it, or the webhook's record
     * of it. Two different subjects on one address (a recycled address) count as unknown.
     */
    public Party byAddress(String email) {
        Set<String> subjects = new HashSet<>(clerkIdentities.subjectsForEmail(email));
        students.findStudentByEmail(email)
                .map(StudentUpdateDto::getClerkUserId)
                .filter(Objects::nonNull)
                .ifPresent(subjects::add);
        return new Party(subjects.size() == 1 ? subjects.iterator().next() : null, email);
    }
}
