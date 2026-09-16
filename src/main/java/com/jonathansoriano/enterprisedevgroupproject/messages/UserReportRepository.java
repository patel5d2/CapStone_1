package com.jonathansoriano.enterprisedevgroupproject.messages;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserReportRepository extends JpaRepository<UserReport, Long> {
}
