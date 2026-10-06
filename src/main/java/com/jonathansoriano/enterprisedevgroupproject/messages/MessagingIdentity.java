package com.jonathansoriano.enterprisedevgroupproject.messages;

import com.jonathansoriano.enterprisedevgroupproject.dto.StudentUpdateDto;
import com.jonathansoriano.enterprisedevgroupproject.repository.StudentRepository;
import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
import com.jonathansoriano.enterprisedevgroupproject.service.StudentIdentityService;
import com.jonathansoriano.enterprisedevgroupproject.webhook.ClerkIdentityRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Who is who in messaging (ADR-012, messaging slice). Every messaging endpoint resolves the
 * caller here and nowhere else.
 *
 * <p><b>Claiming.</b> Rows written before this slice, or addressed to someone who had not
 * been seen yet, carry an address and no subject. The first time their owner calls in, the
 * rows on the owner's <em>verified</em> address are bound to the owner's subject, after
 * which they follow the owner through any address change. This is deliberately not the
 * student-row rule ("never bind by email match", identity-backfill.md rule 1): that rule
 * exists because seeded demo students sit on plausible addresses, and no messaging data is
 * seeded. A messaging row on an address has always meant "whoever holds that address", and
 * the verified token is exactly that person.
 */
@Component
public class MessagingIdentity {

    private final StudentIdentityService studentIdentity;
    private final StudentRepository students;
    private final ClerkIdentityRepository clerkIdentities;
    private final ConversationParticipantRepository participants;
    private final BlockedUserRepository blocks;

    public MessagingIdentity(StudentIdentityService studentIdentity, StudentRepository students,
                             ClerkIdentityRepository clerkIdentities,
                             ConversationParticipantRepository participants, BlockedUserRepository blocks) {
        this.studentIdentity = studentIdentity;
        this.students = students;
        this.clerkIdentities = clerkIdentities;
        this.participants = participants;
        this.blocks = blocks;
    }

    /**
     * The caller, from the verified token only (invariant 1), with their unbound rows
     * claimed. Their address is the token's current one — what listings and the web client
     * use, so new rows agree with them. Rows are claimed on two addresses:
     * <ul>
     *   <li>the token's, unless a different known account is recorded on it (then the rows
     *       stay on the address, still reachable as before, rather than bound by a guess);</li>
     *   <li>the address stored on the caller's bound student row, when it differs — those
     *       rows are theirs from before an address change.</li>
     * </ul>
     *
     * @throws org.springframework.web.server.ResponseStatusException 401 without a subject
     *         or email claim; 409 if the address belongs to a profile bound to another subject
     */
    @Transactional
    public Party caller(Jwt clerkSession) {
        String subject = CurrentUser.subjectOf(clerkSession);
        String storedEmail = studentIdentity.ownerEmailFor(clerkSession);
        Party caller = new Party(subject, CurrentUser.emailOf(clerkSession));

        String holder = byAddress(caller.email()).subject();
        if (holder == null || holder.equals(subject)) {
            claim(caller.email(), subject);
        }
        if (!storedEmail.equalsIgnoreCase(caller.email())) {
            claim(storedEmail, subject);
        }
        return caller;
    }

    private void claim(String email, String subject) {
        participants.claim(email, subject);
        blocks.claimAsBlocker(email, subject);
        blocks.claimAsBlocked(email, subject);
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
