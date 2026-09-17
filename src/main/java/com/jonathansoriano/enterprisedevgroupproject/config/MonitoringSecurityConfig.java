package com.jonathansoriano.enterprisedevgroupproject.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.Assert;

/** A scrape credential authenticates only to Prometheus, never to student APIs. */
@Configuration
@ConditionalOnProperty(name = "campusbridge.monitoring.enabled", havingValue = "true")
public class MonitoringSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain monitoringSecurityFilterChain(
            HttpSecurity http,
            @Value("${campusbridge.monitoring.password:}") String password) throws Exception {
        Assert.hasText(password, "METRICS_PASSWORD must be set when monitoring is enabled");
        var encoder = new BCryptPasswordEncoder();
        var users = new InMemoryUserDetailsManager(User.withUsername("prometheus")
                .password(encoder.encode(password)).roles("MONITORING").build());
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);

        // Keep the provider local to this chain. Registering it globally could let
        // monitoring credentials authenticate to unrelated application endpoints.
        http.securityMatcher("/actuator/prometheus")
                .authenticationManager(new ProviderManager(provider))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").hasRole("MONITORING")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
