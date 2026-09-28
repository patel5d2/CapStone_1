package com.jonathansoriano.enterprisedevgroupproject.profile;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProfileRecordRepository extends JpaRepository<ProfileRecord, Long> {
    Optional<ProfileRecord> findByEmail(String email);

    @Query(value = "SELECT id FROM student WHERE email = :email FOR UPDATE", nativeQuery = true)
    Optional<Long> lockIdByEmail(String email);
}
