package com.jonathansoriano.enterprisedevgroupproject.repository;

import com.jonathansoriano.enterprisedevgroupproject.domain.UserRequest;
import com.jonathansoriano.enterprisedevgroupproject.dto.UserDto;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {
    
    private static final String FIND_USER_BY_EMAIL = """
            SELECT id, role, email, password
            FROM app_user
            WHERE 1 = 1 AND email = :email
            """;

    public static final String INSERT_NEW_APP_USER = """
            INSERT INTO app_user (role, email, password)
            VALUES (:role, :email, :password)
            """;

    public static final String UPDATE_APP_USER = """
            UPDATE app_user
            SET email = :email, password = :password
            WHERE id = :id
            """;
    
    private final NamedParameterJdbcTemplate jdbcTemplate;
    
    public UserRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Changes one account's role. Called only by {@code AdminAccountSeeder}, from server
     * configuration: no request can reach it, which is what keeps the role out of a
     * student's hands (#113).
     *
     * @return the number of rows changed
     */
    public int updateRole(Long id, String role) {
        return jdbcTemplate.update("UPDATE app_user SET role = :role WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("role", role));
    }

    /**
     * The role each of these addresses holds, in one query rather than one per row of an
     * account list. An address with no {@code app_user} row is simply absent from the map.
     */
    public Map<String, String> rolesOf(Collection<String> emails) {
        Map<String, String> roles = new HashMap<>();
        if (emails.isEmpty()) {
            return roles;
        }
        jdbcTemplate.query("SELECT email, role FROM app_user WHERE email IN (:emails)",
                new MapSqlParameterSource("emails", emails),
                row -> { roles.put(row.getString("email"), row.getString("role")); });
        return roles;
    }

    /** One page of banned addresses (#113), in a stable order. */
    public List<String> findBannedEmails(int limit, long offset) {
        return jdbcTemplate.queryForList(
                "SELECT email FROM app_user WHERE role = 'BANNED' ORDER BY email LIMIT :limit OFFSET :offset",
                new MapSqlParameterSource().addValue("limit", limit).addValue("offset", offset), String.class);
    }

    public long countBanned() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM app_user WHERE role = 'BANNED'",
                new MapSqlParameterSource(), Long.class);
        return count == null ? 0 : count;
    }

    /** Removes an account row outright: lifting a ban on an address that has no profile left. */
    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM app_user WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    /**
     * Retrieves a user from the database based on their email address.
     *
     * @param email the email address of the user to be retrieved.
     * @return an {@link Optional} containing the {@link UserDto} if a user with the specified email exists.
     *         If no user is found, an empty {@link Optional} is returned when EmptyResultDataAccessException is thrown.
     */
    public Optional<UserDto> findByEmail(String email) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("email", email);
        
        try {
            UserDto user = jdbcTemplate.queryForObject(
                    FIND_USER_BY_EMAIL, 
                    params, 
                    new BeanPropertyRowMapper<>(UserDto.class, true)
            );
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    /**
     * Inserts a new user record into the database, specifically inserts a new user
     * into the app_user table, using the details provided in the
     * {@link UserRequest} object.
     * The method utilizes the specified role, email, and password from the request
     * object to perform the insertion.
     *
     * @param userRequest the {@link UserRequest} object containing the user's role,
     *                    email, and password.
     * @return an integer indicating the number of rows affected by the insert
     *         operation. A value greater
     *         than 0 indicates that the operation was successful
     */
    public int insertNewUser(UserRequest userRequest) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("role", userRequest.getRole())
                .addValue("email", userRequest.getEmail())
                .addValue("password", userRequest.getPassword());
        try {
            return jdbcTemplate.update(INSERT_NEW_APP_USER, params);
        } catch (Exception ex) {
            throw new RuntimeException("User insertion failed due to a database error", ex);
        }
    }

    /**
     * Updates an existing user's information in the database.
     * This method updates the user's details such as email and password based on the provided {@link UserDto}.
     *
     * @param updatedUser the {@link UserDto} object containing the updated information for the user,
     *                    including their ID, email, and password.
     * @return an integer indicating the number of rows affected by the update operation.
     *         A value greater than 0 indicates a successful update.
     * @throws RuntimeException if the update operation fails due to a database error.
     */
    public int updateUser(UserDto updatedUser) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", updatedUser.getId())
                .addValue("email", updatedUser.getEmail())
                .addValue("password", updatedUser.getPassword());

        StringBuilder sql = new StringBuilder(UPDATE_APP_USER);


        return jdbcTemplate.update(sql.toString(), params);

    }
}
