package com.jonathansoriano.enterprisedevgroupproject.service;

import com.jonathansoriano.enterprisedevgroupproject.PostgresTestConfiguration;
import com.jonathansoriano.enterprisedevgroupproject.domain.StudentRequest;
import com.jonathansoriano.enterprisedevgroupproject.model.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The directory response carries a stable identifier.
 *
 * <p>Until 2026-09-18 it carried none, so the only thing that identified a row was the
 * student's email address — which made the contact details in that payload load-bearing
 * and blocked their removal. This is the prerequisite for S1-07 under every outcome of
 * D-DIRECTORY (#52); it does not itself remove or expose anything new beyond the id.
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
@ExtendWith(SpringExtension.class)
class DirectoryIdentifierTest {

    @Autowired
    StudentService studentService;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void everyDirectoryRowCarriesAnId() {
        List<Student> directory = studentService.find(StudentRequest.builder().build());

        assertTrue(directory.stream().allMatch(s -> s.getId() != null),
                "a row without an id can only be referred to by its email address");
    }

    @Test
    void idsAreDistinctSoTheyCanKeyAList() {
        List<Student> directory = studentService.find(StudentRequest.builder().build());

        Set<Long> distinct = directory.stream().map(Student::getId).collect(Collectors.toSet());
        assertEquals(directory.size(), distinct.size());
    }

    @Test
    void theIdIsTheStudentRowsOwnId() {
        // Not a positional index or a hash of anything: it resolves back to the row, which
        // is what lets a later change address a student without their address.
        Student anyone = studentService.find(StudentRequest.builder().build()).getFirst();

        String emailForThatId = jdbc.queryForObject(
                "SELECT email FROM student WHERE id = ?", String.class, anyone.getId());

        assertEquals(anyone.getEmail(), emailForThatId);
    }
}
