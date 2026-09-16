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

    public static String emailOf(Jwt clerkSession) {
        String email = clerkSession == null ? null : clerkSession.getClaimAsString("email");

        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Clerk session token is missing the 'email' claim");
        }
        return email;
    }
}
