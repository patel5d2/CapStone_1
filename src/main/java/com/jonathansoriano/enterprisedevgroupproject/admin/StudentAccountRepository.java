package com.jonathansoriano.enterprisedevgroupproject.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudentAccountRepository extends JpaRepository<StudentAccount, Long> {

    /**
     * Accounts whose full name or email contains the search text. {@code pattern} is a
     * lower-cased LIKE pattern built by {@code AdminAccountService}, with the user's own
     * {@code %}, {@code _} and {@code !} escaped; it is bound, never concatenated.
     */
    @Query("""
            SELECT s FROM StudentAccount s
            WHERE LOWER(CONCAT(s.firstName, ' ', s.lastName)) LIKE :pattern ESCAPE '!'
               OR LOWER(s.email) LIKE :pattern ESCAPE '!'
            """)
    Page<StudentAccount> search(@Param("pattern") String pattern, Pageable pageable);

    Optional<StudentAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByClerkUserId(String clerkUserId);
}
