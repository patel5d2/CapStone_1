package com.jonathansoriano.enterprisedevgroupproject;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One disposable database per cached Spring context. Spring owns its lifecycle so
 * it stays available until the datasource and other dependent beans shut down.
 * Docker is required: database tests must fail if PostgreSQL cannot be started.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}
