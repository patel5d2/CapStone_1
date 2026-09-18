package com.jonathansoriano.enterprisedevgroupproject.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Objective 1, application half: decides whether a verified Clerk token belongs to a
 * student who may use the platform at all.
 *
 * <p>The rule is that the address sits on a {@code .edu} domain — any {@code .edu}, not
 * a list of named schools. That is deliberately broader than the eight seeded
 * universities: a student whose registrar issues an address this project has never
 * heard of should still be able to sign in, and maintaining a per-school allowlist
 * means a code or data change every time the school list moves.
 *
 * <p>This exists because Clerk's own restrictions are configuration on a hosted
 * dashboard, and configuration drifts. Invariant 3 says only verified institutional
 * accounts may post, message or browse; that has to hold whatever state the dashboard
 * is in, so the application checks it on every request rather than trusting that
 * sign-up was restricted. The two halves are complementary, not redundant: Clerk stops
 * the account being created, this stops a token being useful if one was.
 *
 * <p>Fails closed — anything it cannot positively verify is denied.
 */
@Slf4j
@Component
public class InstitutionalAccessPolicy {

    /** What the user is told when their address is rejected. */
    public static final String NOT_INSTITUTIONAL_MESSAGE =
            "CampusBridge is for verified students. Sign in with your school email address "
                    + "(one ending in .edu) — personal addresses such as Gmail or Outlook cannot be used.";

    /** What the user is told when their account has no second factor and one is required. */
    public static final String NO_SECOND_FACTOR_MESSAGE =
            "Two-factor authentication is required. Add a second factor to your account, "
                    + "then sign in again.";

    private static final String INSTITUTIONAL_SUFFIX = ".edu";

    /**
     * Clerk's factor verification age claim: {@code [first_factor_age, second_factor_age]}
     * in minutes, where {@code -1} means that factor was never verified. A second
     * element of 0 or more is the token asserting the user completed a second factor.
     */
    static final String FACTOR_VERIFICATION_AGE_CLAIM = "fva";

    /**
     * Off by default, and deliberately so.
     *
     * <p>Re-checked against the live instance on 2026-09-18: second factors are now
     * enabled — {@code authenticator_app: ["totp"]}, {@code phone_number: ["phone_code"]}
     * and {@code backup_code} — which corrects the earlier note here that the instance
     * had none. Two things still stand in the way of switching this on:
     * {@code sign_in.second_factor.required} is {@code false}, so accounts that have not
     * enrolled carry no second factor, and the {@code fva} claim has still not been seen
     * on a token from this instance. Turning it on before the team has enrolled refuses
     * every one of those accounts. Decision 015 chooses the factor (TOTP plus backup
     * codes), not this flag.
     */
    private final boolean requireTwoFactor;

    public InstitutionalAccessPolicy(
            @Value("${campusbridge.auth.require-two-factor:false}") boolean requireTwoFactor) {
        this.requireTwoFactor = requireTwoFactor;
    }

    /**
     * Says out loud which rules are live. Two-factor enforcement is the setting most
     * likely to be blamed on "the API is broken" when it is actually doing its job, and
     * it denies every request if Clerk cannot issue a second factor, so it belongs in
     * the startup log rather than only in a config file.
     */
    @PostConstruct
    void logPolicy() {
        if (requireTwoFactor) {
            log.warn("Two-factor enforcement is ENABLED: any token without a second factor "
                    + "(Clerk 'fva' claim) is refused. If two-step verification is not enabled in "
                    + "the Clerk dashboard, this refuses EVERY account. Unset "
                    + "campusbridge.auth.require-two-factor to reverse.");
        } else {
            log.info("Institutional email enforcement active (.edu); two-factor enforcement is off.");
        }
    }

    /** True when this token may be used against the API at all. */
    public boolean grantsStudentAccess(Jwt clerkSession) {
        if (clerkSession == null) {
            return false;
        }
        if (!isInstitutional(clerkSession.getClaimAsString("email"))) {
            return false;
        }
        return !requireTwoFactor || hasSecondFactor(clerkSession.getClaim(FACTOR_VERIFICATION_AGE_CLAIM));
    }

    /** True when the address sits on a {@code .edu} domain. */
    public boolean isInstitutional(String email) {
        String domain = domainOf(email);
        // endsWith(".edu") rather than a contains check: "uc.edu.attacker.com" ends with
        // neither, and requiring the leading dot stops a bare "edu" or "notedu" matching.
        return domain != null && domain.endsWith(INSTITUTIONAL_SUFFIX)
                && domain.length() > INSTITUTIONAL_SUFFIX.length();
    }

    /**
     * The domain part of an address, lower-cased, or null if it is not a single
     * well-formed address. Splitting on the LAST '@' matters: the local part of an
     * address may legally contain one when quoted, and taking the first would let
     * {@code "a@uc.edu"@evil.com} read as an institutional domain.
     */
    static String domainOf(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String trimmed = email.trim();
        int at = trimmed.lastIndexOf('@');
        if (at < 1 || at == trimmed.length() - 1) {
            return null;
        }
        String domain = trimmed.substring(at + 1).toLowerCase(Locale.ROOT);
        // A trailing dot is a legal fully-qualified form that would otherwise fail the
        // suffix test.
        if (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        return domain.isBlank() || domain.chars().anyMatch(Character::isWhitespace) ? null : domain;
    }

    /**
     * Reads Clerk's {@code fva} claim. The second element is the age in minutes of the
     * user's second-factor verification, or -1 if they never completed one.
     *
     * <p>Unverified against the live Clerk instance, which has 2FA switched off and so
     * cannot issue a token that would exercise this. Confirm the claim shape on a real
     * token before setting {@code campusbridge.auth.require-two-factor=true}.
     */
    static boolean hasSecondFactor(Object factorVerificationAgeClaim) {
        if (!(factorVerificationAgeClaim instanceof List<?> ages) || ages.size() < 2) {
            return false;
        }
        return ages.get(1) instanceof Number age && age.longValue() >= 0;
    }
}
