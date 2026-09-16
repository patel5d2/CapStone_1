package com.jonathansoriano.enterprisedevgroupproject.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
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
                        .requestMatchers("/student/**").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                // Bearer tokens are sent explicitly by JavaScript, never as an ambient cookie,
                // so there is no session to fix and no CSRF vector to protect against.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
