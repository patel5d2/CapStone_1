package com.jonathansoriano.enterprisedevgroupproject.school;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Plain JDBC, matching {@code StudentRepository}: {@code university} is not a
 * JPA entity, so the new feature areas read it the same way the student
 * directory already does instead of layering Hibernate over a table it does
 * not own.
 */
@Repository
public class SchoolRepository {

    private static final String SELECT_ALL = "SELECT id, name FROM university ORDER BY name";
    private static final String SELECT_BY_ID = "SELECT id, name FROM university WHERE id = :id";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SchoolRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<School> findAll() {
        return jdbcTemplate.query(SELECT_ALL, new BeanPropertyRowMapper<>(School.class, true));
    }

    public Optional<School> findById(Long id) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id);
        List<School> results = jdbcTemplate.query(SELECT_BY_ID, params, new BeanPropertyRowMapper<>(School.class, true));
        return results.stream().findFirst();
    }
}
