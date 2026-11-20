package com.jonathansoriano.enterprisedevgroupproject.admin;

import com.jonathansoriano.enterprisedevgroupproject.dto.UserDto;
import com.jonathansoriano.enterprisedevgroupproject.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The administrator capability comes from one place, the {@code app_user} role on the
 * token's verified address, and from nothing the caller can put in a token or a request.
 */
class AdminAccessTest {

    private static final String ADMIN_EMAIL = "admin@mail.uc.edu";

    private final UserRepository users = mock(UserRepository.class);
    private final AdminAccess access = new AdminAccess(users);

    private static Jwt.Builder token(String email) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256")
                .subject("user_caller").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        return email == null ? builder : builder.claim("email", email);
    }

    private void role(String email, String role) {
        when(users.findByEmail(email)).thenReturn(Optional.of(UserDto.builder().id(1L).email(email).role(role).build()));
    }

    @Test
    void theAdminRoleOnTheTokensAddress_isAnAdministrator() {
        role(ADMIN_EMAIL, "ADMIN");

        assertTrue(access.isAdmin(token(ADMIN_EMAIL).build()));
        assertDoesNotThrow(() -> access.require(token(ADMIN_EMAIL).build()));
    }

    @Test
    void anOrdinaryStudent_isRefusedWith403() {
        role("student@mail.uc.edu", "USER");

        Jwt student = token("student@mail.uc.edu").build();
        assertFalse(access.isAdmin(student));
        ResponseStatusException refused = assertThrows(ResponseStatusException.class, () -> access.require(student));
        assertEquals(HttpStatus.FORBIDDEN, refused.getStatusCode());
        assertEquals(AdminAccess.NOT_AN_ADMIN_MESSAGE, refused.getReason());
    }

    @Test
    void anAddressWithNoAccountRow_isNotAnAdministrator() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());

        assertFalse(access.isAdmin(token("nobody@mail.uc.edu").build()));
    }

    @Test
    void claimsInTheToken_grantNothing() {
        // Whatever a token says about roles is ignored: only the stored role counts.
        role("student@mail.uc.edu", "USER");

        Jwt dressedUp = token("student@mail.uc.edu")
                .claim("role", "ADMIN")
                .claim("admin", true)
                .claim("public_metadata", java.util.Map.of("role", "ADMIN"))
                .build();

        assertFalse(access.isAdmin(dressedUp));
    }

    @Test
    void theRoleIsMatchedExactly() {
        role("student@mail.uc.edu", "admin");
        assertFalse(access.isAdmin(token("student@mail.uc.edu").build()), "lower-case is not the role");

        role("student@mail.uc.edu", "SUPERADMIN");
        assertFalse(access.isAdmin(token("student@mail.uc.edu").build()));
    }

    @Test
    void theTokensAddressIsNormalisedBeforeTheLookup() {
        role(ADMIN_EMAIL, "ADMIN");

        assertTrue(access.isAdmin(token("  Admin@Mail.UC.edu ").build()));
    }

    @Test
    void aTokenWithNoAddress_is401AndNothingIsLookedUp() {
        ResponseStatusException refused = assertThrows(ResponseStatusException.class,
                () -> access.require(token(null).build()));

        assertEquals(HttpStatus.UNAUTHORIZED, refused.getStatusCode());
        verify(users, never()).findByEmail(anyString());
    }

    @Test
    void aMissingAddress_holdsNoRole() {
        assertFalse(access.holdsAdminRole(null));
        assertFalse(access.holdsAdminRole("   "));
        verify(users, never()).findByEmail(anyString());
    }
}
