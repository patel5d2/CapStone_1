package com.jonathansoriano.enterprisedevgroupproject.security;

import tools.jackson.databind.ObjectMapper;
import com.jonathansoriano.enterprisedevgroupproject.exception.ExceptionWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Spring Security's default 403 is an empty body, which reaches the student as a bare
 * "request failed". The rejection here is not a bug they should retry — it is a rule
 * they need to act on — so the response says which rule and what to do instead, in the
 * same {@link ExceptionWrapper} shape every other error in this application uses.
 */
@Component
public class InstitutionalAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final InstitutionalAccessPolicy policy;

    public InstitutionalAccessDeniedHandler(ObjectMapper objectMapper, InstitutionalAccessPolicy policy) {
        this.objectMapper = objectMapper;
        this.policy = policy;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ExceptionWrapper body = new ExceptionWrapper(
                HttpStatus.FORBIDDEN.value(), reasonFor(currentEmail()), request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }

    /**
     * The caller's email, or null when the request had no Clerk token at all. Read from
     * the token rather than the request so it is the verified value.
     */
    private String currentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwt
                ? jwt.getToken().getClaimAsString("email")
                : null;
    }

    /**
     * Only two things deny a signed-in student: the address is not institutional, or
     * their account has no second factor. Checking the address tells the two apart, so
     * the message names the one that actually applies instead of listing both.
     */
    private String reasonFor(String email) {
        return policy.isInstitutional(email)
                ? InstitutionalAccessPolicy.NO_SECOND_FACTOR_MESSAGE
                : InstitutionalAccessPolicy.NOT_INSTITUTIONAL_MESSAGE;
    }
}
