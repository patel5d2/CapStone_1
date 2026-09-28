package com.jonathansoriano.enterprisedevgroupproject.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The join between {@link InstitutionalAccessPolicy} and the filter chain: which tokens
 * come out carrying {@code ROLE_STUDENT}. {@link InstitutionalAccessEnforcementTest}
 * covers what the chain then does with that authority.
 */
class ClerkJwtAuthenticationConverterTest {

    private final ClerkJwtAuthenticationConverter converter =
            new ClerkJwtAuthenticationConverter(new InstitutionalAccessPolicy(false));

    private static Jwt tokenFor(String email) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject("user_123");
        if (email != null) {
            builder.claim("email", email);
        }
        return builder.build();
    }

    private java.util.Collection<String> authoritiesFor(String email) {
        AbstractAuthenticationToken token = converter.convert(tokenFor(email));
        return token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("a .edu address earns ROLE_STUDENT")
    void institutionalAddressGetsTheRole() {
        assertThat(authoritiesFor("sarah.johnson@mail.uc.edu")).containsExactly("ROLE_STUDENT");
    }

    @Test
    @DisplayName("a personal address earns nothing, so the chain answers 403 not 401")
    void personalAddressGetsNoAuthority() {
        AbstractAuthenticationToken token = converter.convert(tokenFor("dharminp976@gmail.com"));

        assertThat(token.getAuthorities()).isEmpty();
        // Still authenticated: the signature was valid and they really are signed in.
        // That is what makes the refusal a 403 rather than a sign-in loop.
        assertThat(token.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("a token with no email claim earns nothing")
    void missingEmailClaimGetsNoAuthority() {
        assertThat(authoritiesFor(null)).isEmpty();
    }
}
