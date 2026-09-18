package com.jonathansoriano.enterprisedevgroupproject.controller;

import com.jonathansoriano.enterprisedevgroupproject.config.SecurityConfig;
import com.jonathansoriano.enterprisedevgroupproject.exception.SearchNotFoundException;
import com.jonathansoriano.enterprisedevgroupproject.model.Student;
import com.jonathansoriano.enterprisedevgroupproject.service.StudentIdentityService;
import com.jonathansoriano.enterprisedevgroupproject.service.StudentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import com.jonathansoriano.enterprisedevgroupproject.security.ClerkJwtAuthenticationConverter;
import com.jonathansoriano.enterprisedevgroupproject.security.InstitutionalAccessDeniedHandler;
import com.jonathansoriano.enterprisedevgroupproject.security.InstitutionalAccessPolicy;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
// SecurityConfig is imported for its @EnableWebSecurity, which registers the resolver
// behind @AuthenticationPrincipal. Filters stay off: validating a real Clerk token is
// Spring Security's job, and what is tested here is the controller and its status mapping.
@WebMvcTest(controllers = StudentController.class)
// SecurityConfig now depends on the objective 1 access rule, and a @WebMvcTest slice
// does not pick up @Component beans on its own. Filters are still off below: these
// tests are about the controllers, and the rule has its own test.
@Import({SecurityConfig.class, InstitutionalAccessPolicy.class,
        ClerkJwtAuthenticationConverter.class, InstitutionalAccessDeniedHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class StudentControllerTest {

    private static final String SIGNED_IN_EMAIL = "jon@example.com";

    @MockitoBean
    private StudentService service;
    // ADR-012: the controller resolves the caller through this instead of reading the
    // email claim directly. Its own behaviour is covered against a real database in
    // StudentIdentityServiceTest; here it only has to hand back an address.
    @MockitoBean
    private StudentIdentityService identity;
    @Autowired
    private MockMvc mockMvc;

    /**
     * Stands in for the Clerk session token the security filter chain would normally
     * have validated and placed in the security context.
     */
    @BeforeEach
    void signIn() {
        Jwt clerkSession = Jwt.withTokenValue("clerk-session-token")
                .header("alg", "RS256")
                .claim("sub", "user_test")
                .claim("email", SIGNED_IN_EMAIL)
                .build();

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(clerkSession));

        when(identity.ownerEmailFor(any())).thenReturn(SIGNED_IN_EMAIL);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    // Can be used to serialize/deserialize JSON
    // Can also be used to convert Request(StudentSignupRequest,etc) Objects to JSON (POST request body)
    private ObjectMapper objectMapper;

    //We test for exceptions in the controller layer to verify the exception is translated to the
    //correct HTTP response (Status code, body, headers)
    @Test
    void find_Http200() throws Exception {
        // Arrange
        List<Student> expectedList = getStudentList();
        when(service.find(any())).thenReturn(expectedList);

        //Act and Assert (andExpect() is our assertions)
        mockMvc.perform(get("/student")
                .param("firstName", "Jo")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Jon"))
                .andExpect(jsonPath("$[1].firstName").value("John"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void find_Http404_NotFound() throws Exception{
        //Arrange
        when(service.find(any())).thenThrow(SearchNotFoundException.class);
        //Act and Assert (using andExpect() method)
        mockMvc.perform(get("/student")
                .param("firstName", "Grady")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

    }

    @Test
    void createNewStudent_Http201_Created() throws Exception {
        //Arrange
        String requestJson = """
                            {
                              "firstName": "Jon",
                              "lastName": "Sanjuan",
                              "residentCity": "Cincinnati",
                              "residentState": "OH",
                              "universityId": 1,
                              "grade": "Senior",
                              "major": "Computer Science",
                              "email": "jon@example.com",
                              "socialMediaLink": "https://linkedin.com/in/someone"
                            }
                            """;
        String expectedMessage = "Student Signup Successful!";
        when(service.insertNewStudent(any(), any())).thenReturn(expectedMessage);
        //Act and Assert (using andExpect() method)
        mockMvc.perform(post("/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                        .andExpect(status().isCreated())
                        .andExpect(content().string(expectedMessage));
    }

    @Test
    void createNewStudent_MissingBody_Http500_ServerError() throws Exception{
        //Arrange
        //Mimic not having a body in the request
        //No need to mock up the service and exception thrown (Mockito.when... then) since the ServerError exception is thrown before we hit the service call


        //Act and Assert (using andExpect() method)
        mockMvc.perform(post("/student")
                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().is5xxServerError());
    }

    @Test
    void createNewStudent_MissingRequiredField_Http400() throws Exception{
        //Arrange
        //Missing required field (major)
        String invalidRequestJson = """
                                        {
                                          "firstName": "FirstName",
                                          "lastName": "LastName",
                                          "residentCity": "TestCity",
                                          "residentState": "OH",
                                          "universityId": 1,
                                          "grade": "Junior",
                                          "email": "test@example.com",
                                          "socialMediaLink": "Test"
                                        }
                                    """;
        // No need to mock up the service and exception thrown (Mockito.when... then) since validation exception (MethodArgumentNotValidException) is thrown before we hit the service call

        //Act and Assert (using andExpect() method)
        mockMvc.perform(post("/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJson))
                        .andExpect(status().isBadRequest());
    }



    private List<Student> getStudentList() {
        List<Student> studentList = new ArrayList<>();

        Student student1 = Student.builder()
                .firstName("Jon")
                .lastName("Sanjuan")
                .residentCity("Cincinnati")
                .residentState("OH")
                .universityName("University of Cincinnati")
                .grade("Senior")
                .email("jon@mail.com")
                .socialMediaLink("linkedin.com/sorianjn")
                .build();

        Student student2 = Student.builder()
                .firstName("John")
                .lastName("Saint")
                .residentCity("Cincinnati")
                .residentState("OH")
                .universityName("University of Cincinnati")
                .grade("Junior")
                .email("john@mail.com")
                .socialMediaLink("linkedin.com/sorianjn")
                .build();

        studentList.add(student1);
        studentList.add(student2);

        return studentList;
    }
}