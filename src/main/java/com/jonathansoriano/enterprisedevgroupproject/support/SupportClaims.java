package com.jonathansoriano.enterprisedevgroupproject.support;

import com.jonathansoriano.enterprisedevgroupproject.identity.IdentityClaim;
import org.springframework.stereotype.Component;

/**
 * Binds anonymous requests made under a caller's verified address to their subject, so
 * "my requests" follows them through an address change (ADR-012). Once per subject: only
 * the requester ever writes these rows.
 */
@Component
class SupportClaims implements IdentityClaim {

    private final AnonymousRequestRepository requests;

    SupportClaims(AnonymousRequestRepository requests) {
        this.requests = requests;
    }

    @Override
    public void claim(String email, String subject) {
        requests.claim(email, subject);
    }
}
