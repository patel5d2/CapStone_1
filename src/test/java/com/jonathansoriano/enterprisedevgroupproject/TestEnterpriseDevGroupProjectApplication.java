package com.jonathansoriano.enterprisedevgroupproject;

import org.springframework.boot.SpringApplication;

/**
 * Runs the real app on a throwaway embedded PostgreSQL 16: no Docker, no local
 * database. Start it with {@code ./mvnw spring-boot:test-run}. Flyway rebuilds and
 * reseeds the schema on every start, and all data is gone when the app stops.
 */
public class TestEnterpriseDevGroupProjectApplication {

    public static void main(String[] args) {
        SpringApplication.from(EnterpriseDevGroupProjectApplication::main)
                .with(PostgresTestConfiguration.class)
                .run(args);
    }
}
