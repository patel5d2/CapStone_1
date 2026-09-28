package com.jonathansoriano.enterprisedevgroupproject.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Turns a verified Clerk token into an authentication, granting {@code ROLE_STUDENT}
 * only when {@link InstitutionalAccessPolicy} accepts it.
 *
 * <p>Doing this here rather than in each controller is the point. There are 44
 * endpoints and two different ways the caller's email gets read
 * ({@code CurrentUser.emailOf} and a direct {@code getClaimAsString("email")}); a check
 * added per handler is a check somebody forgets on the forty-fifth. Denying the role
 * makes every secured route reject the token at once, and a route added tomorrow
 * inherits the rule.
 *
 * <p>A token that fails the policy still authenticates — the signature is valid and the
 * user really is signed in — it just carries no authority, so Spring answers 403 rather
 * than 401. That distinction matters to the SPA: 401 means "sign in", which would send
 * a non-institutional user round a sign-in loop forever, while 403 means "signed in,
 * not allowed", which is the truth and can be explained to them.
 */
@Component
public class ClerkJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String STUDENT_ROLE = "ROLE_STUDENT";

    private final InstitutionalAccessPolicy policy;

    public ClerkJwtAuthenticationConverter(InstitutionalAccessPolicy policy) {
        this.policy = policy;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt clerkSession) {
        Set<GrantedAuthority> authorities = policy.grantsStudentAccess(clerkSession)
                ? Set.of(new SimpleGrantedAuthority(STUDENT_ROLE))
                : Set.of();

        return new JwtAuthenticationToken(clerkSession, List.copyOf(authorities));
    }
}
