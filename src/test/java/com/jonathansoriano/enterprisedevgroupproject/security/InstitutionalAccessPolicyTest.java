package com.jonathansoriano.enterprisedevgroupproject.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Objective 1's rule is a security boundary, so it gets its own tests rather than
 * relying on the endpoints that happen to sit behind it.
 */
class InstitutionalAccessPolicyTest {

    private InstitutionalAccessPolicy policy(boolean requireTwoFactor) {
        return new InstitutionalAccessPolicy(requireTwoFactor);
    }

    private static Jwt tokenFor(String email, Object fva) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject("user_123");
        if (email != null) {
            builder.claim("email", email);
        }
        if (fva != null) {
            builder.claim(InstitutionalAccessPolicy.FACTOR_VERIFICATION_AGE_CLAIM, fva);
        }
        return builder.build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "sarah.johnson@mail.uc.edu",       // a seeded school
            "student@uc.edu",                  // the same school's other domain
            "student@some-college-we-never-listed.edu",
            "SARAH.JOHNSON@MAIL.UC.EDU",       // Clerk does not normalise case for us
            "  buyer@xavier.edu  ",            // surrounding whitespace
            "student@nku.edu.",                // legal fully-qualified trailing dot
    })
    @DisplayName("any .edu address is institutional, listed school or not")
    void acceptsAnyEduDomain(String email) {
        assertThat(policy(false).isInstitutional(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "dharminp976@gmail.com",           // a real personal address, the common case
            "student@outlook.com",
            "student@xavier.edu.attacker.com", // .edu in the middle, not the suffix
            "student@edu",                     // bare "edu" is not a .edu domain
            "student@notedu",                  // missing the dot
            "student@school.education",        // .edu as a prefix of the real suffix
            "no-at-sign",
            "@xavier.edu",                     // empty local part
            "student@",                        // empty domain
    })
    @DisplayName("everything else is rejected")
    void rejectsEverythingElse(String email) {
        assertThat(policy(false).isInstitutional(email)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("a missing email claim is rejected rather than treated as anonymous")
    void rejectsMissingEmail(String email) {
        assertThat(policy(false).isInstitutional(email)).isFalse();
    }

    @Test
    @DisplayName("the policy judges the domain, not whether the address is well-formed")
    void doesNotRevalidateTheAddress() {
        // The email arrives on a Clerk token that already verified it, so this is not
        // an address validator. An odd local part on an allowed domain is still on that
        // domain, and lastIndexOf('@') means it cannot change which domain that is.
        assertThat(policy(false).isInstitutional("two words@xavier.edu")).isTrue();
    }

    @Test
    @DisplayName("a quoted local part containing @ cannot spoof an allowed domain")
    void splitsOnTheLastAtSign() {
        assertThat(InstitutionalAccessPolicy.domainOf("\"a@uc.edu\"@attacker.com"))
                .isEqualTo("attacker.com");
    }

    @Test
    @DisplayName("with 2FA not required, an institutional token is enough")
    void twoFactorNotRequired() {
        assertThat(policy(false).grantsStudentAccess(tokenFor("buyer@xavier.edu", null))).isTrue();
    }

    @Test
    @DisplayName("with 2FA required, a token that asserts a second factor is accepted")
    void twoFactorRequiredAndPresent() {
        // fva = [first factor age, second factor age] in minutes.
        assertThat(policy(true).grantsStudentAccess(tokenFor("buyer@xavier.edu", List.of(0, 0)))).isTrue();
    }

    @Test
    @DisplayName("with 2FA required, -1 means the second factor was never completed")
    void twoFactorRequiredButNeverVerified() {
        assertThat(policy(true).grantsStudentAccess(tokenFor("buyer@xavier.edu", List.of(5, -1)))).isFalse();
    }

    @Test
    @DisplayName("with 2FA required, a token without the claim is denied, not assumed verified")
    void twoFactorRequiredButClaimAbsent() {
        assertThat(policy(true).grantsStudentAccess(tokenFor("buyer@xavier.edu", null))).isFalse();
    }

    @Test
    @DisplayName("a malformed fva claim is denied")
    void twoFactorClaimMalformed() {
        assertThat(InstitutionalAccessPolicy.hasSecondFactor("not-a-list")).isFalse();
        assertThat(InstitutionalAccessPolicy.hasSecondFactor(List.of(0))).isFalse();
        assertThat(InstitutionalAccessPolicy.hasSecondFactor(Map.of("fva", 0))).isFalse();
    }

    @Test
    @DisplayName("2FA never rescues a non-institutional address")
    void twoFactorDoesNotOverrideTheDomainRule() {
        assertThat(policy(true).grantsStudentAccess(tokenFor("dharminp976@gmail.com", List.of(0, 0)))).isFalse();
    }

    @Test
    @DisplayName("a null token is denied")
    void rejectsNullToken() {
        assertThat(policy(false).grantsStudentAccess(null)).isFalse();
    }
}
