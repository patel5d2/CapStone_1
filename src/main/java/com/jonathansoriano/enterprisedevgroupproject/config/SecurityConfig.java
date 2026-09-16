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
                        // Static pages are served to everyone. A browser cannot attach a bearer
                        // header to a top-level navigation, so the signed-in gate for pages is
                        // enforced client-side by Clerk (see /js/clerk-auth.js). The data below
                        // is what is actually protected.
                        .requestMatchers("/", "/*.html", "/css/**", "/js/**", "/favicon.ico").permitAll()
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
