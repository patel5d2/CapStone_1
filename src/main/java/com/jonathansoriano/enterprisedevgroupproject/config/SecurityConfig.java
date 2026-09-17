package com.jonathansoriano.enterprisedevgroupproject.config;

import com.jonathansoriano.enterprisedevgroupproject.security.ClerkJwtAuthenticationConverter;
import com.jonathansoriano.enterprisedevgroupproject.security.InstitutionalAccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authentication is delegated to Clerk. The browser signs the user in with Clerk,
 * then attaches the Clerk session token as a {@code Authorization: Bearer <jwt>}
 * header on API calls. This application never sees a password: it only validates
 * the JWT signature against Clerk's JWKS endpoint (configured via
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** {@code hasRole} prepends {@code ROLE_}; the authority granted is {@code ROLE_STUDENT}. */
    private static final String STUDENT = "STUDENT";

    private final ClerkJwtAuthenticationConverter jwtAuthenticationConverter;
    private final InstitutionalAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(ClerkJwtAuthenticationConverter jwtAuthenticationConverter,
                          InstitutionalAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // The React bundle is served to everyone. A browser cannot attach a bearer
                        // header to a top-level navigation or to an asset request, so the signed-in
                        // gate for pages is enforced client-side by Clerk (see RequireAuth in the
                        // SPA router). The data below is what is actually protected.
                        .requestMatchers("/", "/index.html", "/assets/**", "/*.svg", "/*.ico", "/*.png",
                                "/*.webmanifest", "/*.txt").permitAll()
                        // Client-side routes: Spring forwards these to index.html
                        // (see SpaForwardingConfig) so a refresh or deep link still loads the app.
                        .requestMatchers("/marketplace", "/messages", "/community", "/support", "/directory",
                                "/profile", "/sign-in/**", "/sign-up/**").permitAll()
                        // Liveness only, so the container healthcheck and orchestrators can see
                        // whether the app is up. Everything else under /actuator — metrics,
                        // prometheus, env, info — stays authenticated: those describe the system
                        // and must not be readable anonymously.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Objective 1 / invariant 3: the data surfaces need a verified
                        // institutional account, not merely a valid Clerk token.
                        // ROLE_STUDENT is granted by ClerkJwtAuthenticationConverter only
                        // when the token's email sits on a .edu domain.
                        .requestMatchers("/student/**").hasRole(STUDENT)
                        .requestMatchers("/api/**").hasRole(STUDENT)
                        // Actuator's remaining endpoints describe the system, not student
                        // data, and are scraped by Prometheus rather than by a student, so
                        // they stay on plain authentication. When monitoring is enabled,
                        // MonitoringSecurityConfig handles only the Prometheus scrape
                        // route first, using a separate service credential.
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        // Without this the resource server answers its own bare 403 and
                        // the student never learns they need a .edu address.
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler))
                // Bearer tokens are sent explicitly by JavaScript, never as an ambient cookie,
                // so there is no session to fix and no CSRF vector to protect against.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
