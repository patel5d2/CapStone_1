package com.jonathansoriano.enterprisedevgroupproject.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads the verified email address out of a Clerk session token, the same way
 * StudentController does. New feature areas (marketplace, messages, community,
 * support) share this instead of each re-declaring the claim lookup.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * The Clerk user ID (the token's {@code sub}) of the caller — the stable identity
     * ADR-012 keys ownership on. Unlike the email claim this never changes when a
     * student updates their address in Clerk, which is the whole point of S1-02.
     *
     * <p>Fails 401 rather than returning null: a token with no subject cannot be tied
     * to anything, and invariant 1 says identity comes from the verified token only.
     */
    public static String subjectOf(Jwt clerkSession) {
        String subject = clerkSession == null ? null : clerkSession.getSubject();

        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Clerk session token is missing the 'sub' claim");
        }
        return subject;
    }

    public static String emailOf(Jwt clerkSession) {
        String email = clerkSession == null ? null : clerkSession.getClaimAsString("email");

        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Clerk session token is missing the 'email' claim");
        }
        return email;
    }
}
