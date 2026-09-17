package com.jonathansoriano.enterprisedevgroupproject.security;

import com.jonathansoriano.enterprisedevgroupproject.config.SecurityConfig;
import com.jonathansoriano.enterprisedevgroupproject.school.SchoolController;
import com.jonathansoriano.enterprisedevgroupproject.school.SchoolRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Objective 1's second half: the filter chain actually requires the authority, and a
 * refusal explains itself. The policy alone could be perfect and this would still be
 * broken if someone dropped {@code .hasRole(...)} from {@code SecurityConfig}.
 *
 * <p>Note what this does NOT cover. Spring's {@code jwt()} post-processor installs its
 * own authentication, so {@link ClerkJwtAuthenticationConverter} never runs here and the
 * authorities below are set by hand. Which tokens earn {@code ROLE_STUDENT} is
 * {@link ClerkJwtAuthenticationConverterTest}'s job; this is only about what the chain
 * does with that answer.
 *
 * <p>{@code /api/schools} stands in for all 44 endpoints because the rule is applied to
 * the {@code /api/**} and {@code /student/**} matchers, not per handler.
 */
@WebMvcTest(controllers = SchoolController.class)
@Import({SecurityConfig.class, InstitutionalAccessPolicy.class,
        ClerkJwtAuthenticationConverter.class, InstitutionalAccessDeniedHandler.class})
class InstitutionalAccessEnforcementTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SchoolRepository schoolRepository;

    @Test
    @DisplayName("a token carrying ROLE_STUDENT reaches the endpoint")
    void studentRoleIsAllowed() throws Exception {
        mockMvc.perform(get("/api/schools")
                        .with(jwt().jwt(j -> j.claim("email", "sarah.johnson@mail.uc.edu"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a valid token without the role is refused — authentication alone is not enough")
    void authenticatedWithoutRoleIsRefused() throws Exception {
        mockMvc.perform(get("/api/schools")
                        .with(jwt().jwt(j -> j.claim("email", "sarah.johnson@mail.uc.edu"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a personal address is refused with an explanation, not an empty 403")
    void personalAddressIsRefusedWithAMessage() throws Exception {
        mockMvc.perform(get("/api/schools")
                        .with(jwt().jwt(j -> j.claim("email", "dharminp976@gmail.com"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(InstitutionalAccessPolicy.NOT_INSTITUTIONAL_MESSAGE))
                .andExpect(jsonPath("$.path").value("/api/schools"));
    }


    @Test
    @DisplayName("the legacy /student surface is behind the same rule")
    void legacyStudentSurfaceIsCovered() throws Exception {
        mockMvc.perform(get("/student")
                        .with(jwt().jwt(j -> j.claim("email", "dharminp976@gmail.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("no token at all is still 401, not 403")
    void anonymousIsUnauthorised() throws Exception {
        mockMvc.perform(get("/api/schools")).andExpect(status().isUnauthorized());
    }
}
