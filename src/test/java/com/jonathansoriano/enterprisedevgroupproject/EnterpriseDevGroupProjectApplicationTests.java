package com.jonathansoriano.enterprisedevgroupproject;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EnterpriseDevGroupProjectApplicationTests {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void contextLoadsWithH2AndEverySchemaTable() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
        }

        // Hibernate validates the entity tables, but would miss the plain JDBC tables
        // (university, student, app_user, clerk_identity). Check the whole schema here.
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'
                """, String.class)).contains(
                "university", "student", "app_user", "listing", "listing_photo",
                "listing_favorite", "listing_report", "conversation", "conversation_participant",
                "message", "blocked_user", "user_report", "post", "post_comment", "post_like",
                "app_group", "group_membership", "event", "support_resource", "anonymous_request",
                "clerk_identity", "profile_privacy", "image_asset");
    }

    @Test
    void dataSqlSeedsSchoolsAndDemoProfilesWithoutLocalCredentials() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM university", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM student", Integer.class)).isEqualTo(33);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT university_id) FROM student", Integer.class))
                .isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class)).isEqualTo(33);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE password IS NOT NULL", Integer.class))
                .isZero();
    }
}
