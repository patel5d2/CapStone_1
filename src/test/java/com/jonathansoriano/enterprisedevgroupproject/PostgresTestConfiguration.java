package com.jonathansoriano.enterprisedevgroupproject;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * One disposable PostgreSQL 16 per cached Spring context, run from embedded binaries
 * in a temp directory: no Docker, no developer database. Spring closes it (and deletes
 * its data) after the datasource shuts down. A test that forgets to import this still
 * fails, because src/test's datasource URL is deliberately unreachable.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    @Bean(destroyMethod = "close")
    EmbeddedPostgres embeddedPostgres() throws IOException {
        return EmbeddedPostgres.start();
    }

    @Bean
    DataSource dataSource(EmbeddedPostgres postgres) {
        return postgres.getPostgresDatabase();
    }
}
