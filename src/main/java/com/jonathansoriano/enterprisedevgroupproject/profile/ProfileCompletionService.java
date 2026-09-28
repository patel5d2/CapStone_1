package com.jonathansoriano.enterprisedevgroupproject.profile;

import com.jonathansoriano.enterprisedevgroupproject.domain.*;
import com.jonathansoriano.enterprisedevgroupproject.image.ProfileImageService;
import com.jonathansoriano.enterprisedevgroupproject.model.Student;
import com.jonathansoriano.enterprisedevgroupproject.model.StudentAccountDetails;
import com.jonathansoriano.enterprisedevgroupproject.profile.dto.ProfileVisibility;
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

@Service
public class ProfileCompletionService {
    private final StudentService students;
    private final StudentIdentityService identity;
    private final ProfileRecordRepository records;
    private final ProfilePrivacyRepository privacy;
    private final ProfileImageService images;
    private final com.jonathansoriano.enterprisedevgroupproject.school.SchoolRepository schools;

    public ProfileCompletionService(StudentService students, StudentIdentityService identity,
            ProfileRecordRepository records, ProfilePrivacyRepository privacy, ProfileImageService images,
            com.jonathansoriano.enterprisedevgroupproject.school.SchoolRepository schools) {
        this.students = students;
        this.identity = identity;
        this.records = records;
        this.privacy = privacy;
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
        response.setVisibility(visibility(row.getId()));
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
        saveVisibility(row.getId(), request.getVisibility());
        return response;
    }

    @Transactional
    public String update(Jwt jwt, EditStudentDetailsRequest request) {
        String email = identity.ownerEmailFor(jwt);
        // Serialize updates to the profile, preferences and image attachment together.
        Long id = records.lockIdByEmail(email).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Complete your profile first."));
        ProfileRecord row = records.findById(id).orElseThrow();
        String subject = CurrentUser.subjectOf(jwt);
        if (row.getClerkUserId() != null && !row.getClerkUserId().equals(subject))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This profile belongs to another account.");
        images.attach(subject, row.getId(), request.getPhotoUrl());
        String response = students.updateStudent(email, request);
        saveVisibility(row.getId(), request.getVisibility());
        return response;
    }

    private ProfileVisibility visibility(Long id) {
        return privacy.findById(id).map(ProfilePrivacy::visibility).orElse(ProfileVisibility.privateByDefault());
    }

    private void saveVisibility(Long id, ProfileVisibility value) {
        // Old clients omitting preferences must not reset an existing choice.
        if (value == null) return;
        ProfilePrivacy row = privacy.findById(id).orElseGet(ProfilePrivacy::new);
        row.setStudentId(id);
        row.apply(value);
        privacy.save(row);
    }

    @Transactional(readOnly = true)
    public List<Student> directory(StudentRequest request) {
        List<Student> found = students.find(request);
        List<Long> ids = found.stream().map(Student::getId).toList();
        Map<Long, ProfilePrivacy> settings = privacy.findAllById(ids).stream()
                .collect(Collectors.toMap(ProfilePrivacy::getStudentId, Function.identity()));
        Map<Long, ProfileRecord> rows = records.findAllById(ids).stream()
                .collect(Collectors.toMap(ProfileRecord::getId, Function.identity()));
        List<Student> result = new ArrayList<>();
        for (Student student : found) {
            ProfileVisibility visible = settings.containsKey(student.getId())
                    ? settings.get(student.getId()).visibility() : ProfileVisibility.privateByDefault();
            // A hidden major cannot be inferred by using it as a search predicate.
            if (request.getMajor() != null && !request.getMajor().isBlank() && !visible.showMajor()) continue;
            ProfileRecord row = rows.get(student.getId());
            result.add(Student.builder().id(student.getId()).firstName(student.getFirstName())
                    .lastName(student.getLastName()).residentCity(student.getResidentCity())
                    .residentState(student.getResidentState()).universityName(student.getUniversityName())
                    .grade(student.getGrade()).major(visible.showMajor() ? student.getMajor() : null)
                    .graduationYear(visible.showGraduationYear() && row != null ? row.getGraduationYear() : null)
                    .bio(visible.showBio() && row != null ? row.getBio() : null)
                    .photoUrl(visible.showPhoto() && row != null ? row.getPhotoUrl() : null).build());
        }
        return result;
    }
}
