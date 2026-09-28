package com.jonathansoriano.enterprisedevgroupproject;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class EnterpriseDevGroupProjectApplicationTests {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Flyway flyway;

    @Test
    void contextLoadsWithPostgres16AndAllMigratedTables() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
        }

        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbc.queryForList("""
                SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank
                """, String.class)).contains("1", "2", "3");

        // Hibernate validates the entity tables, but would miss the three JDBC tables
        // that caused the original deployment crash. Check the whole baseline here.
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'
                """, String.class)).contains(
                "university", "student", "app_user", "listing", "listing_photo",
                "listing_favorite", "listing_report", "conversation", "conversation_participant",
                "message", "blocked_user", "user_report", "post", "post_comment", "post_like",
                "app_group", "group_membership", "event", "support_resource", "anonymous_request");
    }

    @Test
    void migrationsSeedSchoolsAndDemoProfilesWithoutLocalCredentials() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM university", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM student", Integer.class)).isEqualTo(33);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT university_id) FROM student", Integer.class))
                .isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class)).isEqualTo(33);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE password IS NOT NULL", Integer.class))
                .isZero();
    }

    @Test
    void migratingAgainPreservesDataAndDoesNotReapplySeeds() {
        Long schoolId = jdbc.queryForObject("INSERT INTO university (name) VALUES (?) RETURNING id",
                Long.class, "Migration persistence test school");
        try {
            // A new Flyway instance models the next startup against an existing DB.
            Flyway nextStartup = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(flyway.getConfiguration().getLocations())
                    .load();
            assertThat(nextStartup.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForObject("SELECT name FROM university WHERE id = ?", String.class, schoolId))
                    .isEqualTo("Migration persistence test school");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM student", Integer.class)).isEqualTo(33);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class)).isEqualTo(33);
        } finally {
            jdbc.update("DELETE FROM university WHERE id = ?", schoolId);
        }
    }
}
