package com.jonathansoriano.enterprisedevgroupproject.service;

import com.jonathansoriano.enterprisedevgroupproject.domain.EditStudentDetailsRequest;
import com.jonathansoriano.enterprisedevgroupproject.domain.StudentRequest;
import com.jonathansoriano.enterprisedevgroupproject.domain.StudentSignupRequest;
import com.jonathansoriano.enterprisedevgroupproject.domain.UserRequest;
import com.jonathansoriano.enterprisedevgroupproject.dto.StudentAccountDetailsDto;
import com.jonathansoriano.enterprisedevgroupproject.dto.StudentDto;
import com.jonathansoriano.enterprisedevgroupproject.dto.StudentUpdateDto;
import com.jonathansoriano.enterprisedevgroupproject.dto.UserDto;
import com.jonathansoriano.enterprisedevgroupproject.exception.EmailAlreadyExistsException;
import com.jonathansoriano.enterprisedevgroupproject.exception.SearchNotFoundException;
import com.jonathansoriano.enterprisedevgroupproject.model.Student;
import com.jonathansoriano.enterprisedevgroupproject.model.StudentAccountDetails;
import com.jonathansoriano.enterprisedevgroupproject.repository.StudentRepository;
import com.jonathansoriano.enterprisedevgroupproject.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class StudentService {
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    public StudentService(StudentRepository studentRepository, UserRepository userRepository) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
    }

    /**
     * Searches for students based on the given request criteria and returns a list
     * of matching students.
     * If no students are found, a {@link SearchNotFoundException} is thrown.
     *
     * @param request the {@link StudentRequest} object containing the search
     *                parameters for retrieving students.
     * @return a list of {@link Student} objects that match the search criteria.
     * @throws SearchNotFoundException if no students are found matching the given
     *                                 criteria.
     */
    @Cacheable(value = "students")
    public List<Student> find(StudentRequest request) {
        List<Student> students = buildStudentListFromDtoList(studentRepository.find(request));

        if (CollectionUtils.isEmpty(students)) {
            throw new SearchNotFoundException("Student Not found!");
        }
        return students;

    }

    /**
     * Finds the student account details by the provided email.
     *
     * @param usersUsername the email of the user to search for
     * @return the student account details associated with the given email
     * @throws SearchNotFoundException if no student is found with the given email
     */
    public StudentAccountDetails findByEmail(String usersUsername) {
        StudentAccountDetailsDto studentDto = studentRepository.findByEmail(usersUsername)
                .orElseThrow(
                        () -> new SearchNotFoundException("Student account not found with email: " + usersUsername));

        return buildStudentAccountDetailFromDto(studentDto, usersUsername);
    }

    /**
     * Inserts a new student into the system by creating corresponding entries in
     * the user table
     * and the student table. No credential is stored: the student has already
     * authenticated with Clerk. Transactional annotation is used to ensure both writes
     * either succeed together or both roll back together. Without it, a failure on
     * the second insert (student) after the first insert (user) succeeded would
     * leave
     * the database in an inconsistent state a user account with no matching student
     * profile.
     *
     * @param student The {@link StudentSignupRequest} object containing the new
     *                student's details
     *                such as name, email, resident information, and university
     *                details.
     * @return A message indicating the success or failure of the student signup
     *         operation.
     *         If successful, returns "Student Signup Successful!".
     *         Otherwise, an exception is thrown.
     * @throws RuntimeException if the insertion into the student table or user
     *                          table fails.
     *                          Specific exceptions for these failures could be
     *                          implemented in the future.
     */
    @Transactional
    public String insertNewStudent(StudentSignupRequest student, String clerkUserId) {
        // One directory row per Clerk account (ADR-012). Checked before the email check
        // because a student who changed their address in Clerk would otherwise pass it
        // and then hit the clerk_user_id unique constraint as an opaque 500.
        if (studentRepository.findStudentByClerkUserId(clerkUserId).isPresent()) {
            throw new EmailAlreadyExistsException(
                    "This account already has a profile. Edit it instead of creating another.");
        }

        UserDto userDto = userRepository.findByEmail(student.getEmail()).orElse(null);

        if (userDto != null) {
            throw new EmailAlreadyExistsException(
                    "An existing account already exists with the email: " + student.getEmail());
        }

        // Step 1: Build a UserRequest DTO from the signup request to insert into
        // app_user table. There is no password to store: Clerk holds the credential.
        UserRequest userRequest = buildUserRequestFromStudentSignupRequest(student);

        // Step 2: Insert the user record into the app_user table first
        int userInsertionResult = userRepository.insertNewUser(userRequest);

        // Step 3: Insert the student profile into the student table, bound to the Clerk
        // subject that created it. This is the only path that writes clerk_user_id, and
        // it writes it from the verified token — never from the request body.
        int studentInsertionResult = studentRepository.insertNewStudent(student, clerkUserId);

        return "Student Signup Successful!";
    }

    /**
     * Updates the details of an existing student and their associated user account.
     *
     * @param username       The email of the student whose details need to be
     *                       updated. This serves as a unique identifier.
     * @param studentDetails The object containing the updated details to be applied
     *                       to the student and user account.
     * @return A confirmation message indicating the successful update of the
     *         account.
     * @throws SearchNotFoundException If the student associated with the provided
     *                                 username cannot be found.
     */
    @Transactional
    public String updateStudent(String username, EditStudentDetailsRequest studentDetails) {
        // Do a find in the Student table using the username (email) and assign returned
        // Student from repo to Student object
        StudentUpdateDto outdatedStudent = studentRepository.findStudentByEmail(username)
                .orElseThrow(() -> new SearchNotFoundException("Student Not found!"));

        // Set the values of the Student object with the values in the
        // EditStudentDetailsRequest object.
        // The email is not editable here: it is the Clerk identity this record hangs
        // off, and it is changed through Clerk's own user profile UI.
        StudentUpdateDto updatedStudent = updateStudentUpdateDto(outdatedStudent, studentDetails);

        // Send Updated Student Object to the Repository layer and wait to see if the
        // update was successful
        int studentResult = studentRepository.updateStudent(updatedStudent);

        return "Account Updated Successfully!";
    }

    /**
     * Updates the provided StudentUpdateDto object with the values from the
     * EditStudentDetailsRequest object.
     *
     * @param studentUpdateDto the StudentUpdateDto object to be updated
     * @param studentDetails   the EditStudentDetailsRequest object containing the
     *                         new student details
     * @return the updated StudentUpdateDto object
     */
    private StudentUpdateDto updateStudentUpdateDto(StudentUpdateDto studentUpdateDto,
            EditStudentDetailsRequest studentDetails) {
        studentUpdateDto.setFirstName(studentDetails.getFirstName());
        studentUpdateDto.setLastName(studentDetails.getLastName());
        studentUpdateDto.setResidentCity(studentDetails.getResidentCity());
        studentUpdateDto.setResidentState(studentDetails.getResidentState());
        studentUpdateDto.setGrade(studentDetails.getGrade());
        studentUpdateDto.setMajor(studentDetails.getMajor());
        studentUpdateDto.setSocialMediaLink(studentDetails.getSocialMediaLink());
        studentUpdateDto.setGraduationYear(studentDetails.getGraduationYear());
        studentUpdateDto.setBio(studentDetails.getBio());
        studentUpdateDto.setPhotoUrl(studentDetails.getPhotoUrl());

        // universityId and email are NOT copied from the request. Both identify the
        // account rather than describe it: the school decides which directory, theme and
        // school-scoped surfaces a student belongs to (objectives 2, 5 and 8), and the
        // email is the key the row hangs off. Copying the body's universityId here let any
        // signed-in student move themselves to another school by editing their own
        // profile. The stored values are kept instead, so a value sent in the body is
        // overwritten server-side before it reaches the database.
        //
        // This keeps the school as it is; it does not yet DERIVE it. Deriving the school
        // from the verified email domain is S1-03, which is blocked on D-SCHOOLS.

        return studentUpdateDto;
    }

    /**
     * Converts a {@link StudentSignupRequest} object into a {@link UserRequest}
     * object.
     * This method is used to prepare a user request for inserting a user into the
     * system's user table.
     * The resulting {@link UserRequest} includes the user's email and a default role
     * of "USER". There is no password: Clerk authenticates the user.
     *
     * @param studentSignupRequest the source {@link StudentSignupRequest}
     *                             containing the student's signup details.
     * @return a {@link UserRequest} object containing the mapped user data.
     */
    private static UserRequest buildUserRequestFromStudentSignupRequest(StudentSignupRequest studentSignupRequest) {
        return UserRequest.builder()
                .role("USER")
                .email(studentSignupRequest.getEmail())
                .build();
    }

    /**
     * Converts a list of StudentDto objects into a list of Student objects.
     * Each StudentDto in the input list is transformed into a corresponding Student
     * instance by invoking the buildStudentFromDto method.
     *
     * @param dtoList the list of StudentDto objects to be converted into Student
     *                objects.
     * @return a list of Student objects created from the given list of StudentDto
     *         objects.
     */
    static List<Student> buildStudentListFromDtoList(List<StudentDto> dtoList) {
        List<Student> studentList = new ArrayList<>();

        for (StudentDto dto : dtoList) {
            studentList.add(buildStudentFromDto(dto));
        }
        return studentList;
    }

    /**
     * Builds a {@link Student} object from a given {@link StudentDto}.
     * The method maps the fields from the provided StudentDto to a new Student
     * instance.
     *
     * @param dto the {@link StudentDto} containing student data to be converted
     *            into a {@link Student}.
     * @return a new {@link Student} object populated with the data from the
     *         provided {@link StudentDto}.
     */
    static Student buildStudentFromDto(StudentDto dto) {
        return Student.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .residentCity(dto.getResidentCity())
                .residentState(dto.getResidentState())
                .universityName(dto.getUniversityName())
                .grade(dto.getGrade())
                .major(dto.getMajor())
                .email(dto.getEmail())
                .socialMediaLink(dto.getSocialMediaLink())
                .build();
    }

    static StudentAccountDetails buildStudentAccountDetailFromDto(StudentAccountDetailsDto studentDto, String email) {

        return StudentAccountDetails.builder()
                .firstName(studentDto.getFirstName())
                .lastName(studentDto.getLastName())
                .residentCity(studentDto.getResidentCity())
                .residentState(studentDto.getResidentState())
                .universityName(studentDto.getUniversityName())
                .grade(studentDto.getGrade())
                .major(studentDto.getMajor())
                .email(email)
                .socialMediaLink(studentDto.getSocialMediaLink())
                .build();
    }

}
