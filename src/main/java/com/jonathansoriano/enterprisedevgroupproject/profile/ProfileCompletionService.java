package com.jonathansoriano.enterprisedevgroupproject.profile;

import com.jonathansoriano.enterprisedevgroupproject.domain.*;
import com.jonathansoriano.enterprisedevgroupproject.image.ProfileImageService;
import com.jonathansoriano.enterprisedevgroupproject.model.Student;
import com.jonathansoriano.enterprisedevgroupproject.model.StudentAccountDetails;
import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
import com.jonathansoriano.enterprisedevgroupproject.service.StudentIdentityService;
import com.jonathansoriano.enterprisedevgroupproject.service.StudentService;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * A student's own profile (read, create, update) and the directory other students see.
 *
 * <p>What another student sees is the same for every profile (#112): name, school, year of
 * study, city and state, major, graduation year, bio and photo. There is no per-student
 * choice. Email and social link are never part of the directory response
 * ({@code @JsonIgnore} on {@link Student}).
 */
@Service
public class ProfileCompletionService {
    private final StudentService students;
    private final StudentIdentityService identity;
    private final ProfileRecordRepository records;
    private final ProfileImageService images;
    private final com.jonathansoriano.enterprisedevgroupproject.school.SchoolRepository schools;

    public ProfileCompletionService(StudentService students, StudentIdentityService identity,
            ProfileRecordRepository records, ProfileImageService images,
            com.jonathansoriano.enterprisedevgroupproject.school.SchoolRepository schools) {
        this.students = students;
        this.identity = identity;
        this.records = records;
        this.images = images;
        this.schools = schools;
    }

    @Transactional(readOnly = true)
    public StudentAccountDetails read(Jwt jwt) {
        String email = identity.ownerEmailFor(jwt);
        ProfileRecord row = records.findByEmail(email).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Complete your profile to join the directory."));
        StudentAccountDetails response = students.findByEmail(email);
        response.setUniversityId(row.getUniversityId());
        return response;
    }

    @Transactional
    public String create(Jwt jwt, StudentSignupRequest request) {
        String subject = CurrentUser.subjectOf(jwt);
        request.setEmail(CurrentUser.emailOf(jwt));
        if (schools.findById(request.getUniversityId().longValue()).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an available school.");
        String response = students.insertNewStudent(request, subject);
        ProfileRecord row = records.findByEmail(request.getEmail()).orElseThrow();
        images.attach(subject, row.getId(), request.getPhotoUrl());
        return response;
    }

    @Transactional
    public String update(Jwt jwt, EditStudentDetailsRequest request) {
        String email = identity.ownerEmailFor(jwt);
        // Serialize updates to the profile and image attachment together.
        Long id = records.lockIdByEmail(email).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Complete your profile first."));
        ProfileRecord row = records.findById(id).orElseThrow();
        String subject = CurrentUser.subjectOf(jwt);
        if (row.getClerkUserId() != null && !row.getClerkUserId().equals(subject))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This profile belongs to another account.");
        images.attach(subject, row.getId(), request.getPhotoUrl());
        return students.updateStudent(email, request);
    }

    /** The directory: every profile carries the same fields, whoever it belongs to (#112). */
    @Transactional(readOnly = true)
    public List<Student> directory(StudentRequest request) {
        List<Student> found = students.find(request);
        // Graduation year, bio and photo are not in the legacy directory query.
        Map<Long, ProfileRecord> rows = records.findAllById(found.stream().map(Student::getId).toList()).stream()
                .collect(Collectors.toMap(ProfileRecord::getId, Function.identity()));
        List<Student> result = new ArrayList<>();
        for (Student student : found) {
            ProfileRecord row = rows.get(student.getId());
            result.add(Student.builder().id(student.getId()).firstName(student.getFirstName())
                    .lastName(student.getLastName()).residentCity(student.getResidentCity())
                    .residentState(student.getResidentState()).universityName(student.getUniversityName())
                    .grade(student.getGrade()).major(student.getMajor())
                    .graduationYear(row == null ? null : row.getGraduationYear())
                    .bio(row == null ? null : row.getBio())
                    .photoUrl(row == null ? null : row.getPhotoUrl()).build());
        }
        return result;
    }
}
