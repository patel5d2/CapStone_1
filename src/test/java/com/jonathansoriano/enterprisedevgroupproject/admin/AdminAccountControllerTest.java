package com.jonathansoriano.enterprisedevgroupproject.admin;

import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminPageResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminStudentResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminStudentUpdateRequest;
import com.jonathansoriano.enterprisedevgroupproject.config.SecurityConfig;
import com.jonathansoriano.enterprisedevgroupproject.exception.SearchNotFoundException;
import com.jonathansoriano.enterprisedevgroupproject.security.ClerkJwtAuthenticationConverter;
import com.jonathansoriano.enterprisedevgroupproject.security.InstitutionalAccessDeniedHandler;
import com.jonathansoriano.enterprisedevgroupproject.security.InstitutionalAccessPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP face of account management, with the real security filter chain in front of it:
 * who gets in, and how each refusal and failure reaches the client.
 *
 * <p>Whether a caller is an administrator is decided inside {@link AdminAccountService}
 * ({@code AdminAccountServiceTest} covers it against a real database); here the service is a
 * mock, and what is checked is that its answers arrive as the right status and message.
 */
@WebMvcTest(controllers = AdminAccountController.class)
@Import({SecurityConfig.class, InstitutionalAccessPolicy.class,
        ClerkJwtAuthenticationConverter.class, InstitutionalAccessDeniedHandler.class})
class AdminAccountControllerTest {

    private static final String VALID_UPDATE = """
            {"firstName":"Sarah","lastName":"Johnson","residentCity":"Cincinnati","residentState":"OH",
             "universityId":1,"grade":"Senior","major":"Computer Science","socialMediaLink":null,
             "graduationYear":2027,"bio":null}
            """;

    @Autowired
    private MockMvc mockMvc;

    // The ban check (#113) sits beside the access policy in the converter. A mock answers
    // "not banned"; what a ban does is AccountBansTest's and the converter test's job.
    @MockitoBean
    private com.jonathansoriano.enterprisedevgroupproject.security.AccountBans accountBans;

    @MockitoBean
    private AdminAccountService service;

    /** A signed-in account that passed the platform's own gate (email and second factor). */
    private static RequestPostProcessor signedIn() {
        return jwt().jwt(j -> j.subject("user_caller").claim("email", "caller@mail.uc.edu"))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private static AdminStudentResponse sarah() {
        return new AdminStudentResponse(5L, "Sarah", "Johnson", "sarah.johnson@mail.uc.edu", 1L,
                "University of Cincinnati", "Senior", "Computer Science", "Cincinnati", "OH",
                null, 2027, null, false, false, false);
    }

    @Test
    void noToken_is401_andTheServiceIsNeverReached() throws Exception {
        mockMvc.perform(get("/api/admin/students")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/students/5").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/students/5")).andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void aTokenThatFailedThePlatformGate_is403_andTheServiceIsNeverReached() throws Exception {
        mockMvc.perform(delete("/api/admin/students/5")
                        .with(jwt().jwt(j -> j.claim("email", "caller@mail.uc.edu"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void aStudentWhoIsNotAnAdministrator_gets403WithAReason() throws Exception {
        ResponseStatusException notAdmin =
                new ResponseStatusException(HttpStatus.FORBIDDEN, AdminAccess.NOT_AN_ADMIN_MESSAGE);
        when(service.search(any(), any(), anyInt(), anyInt())).thenThrow(notAdmin);
        when(service.get(any(), anyLong())).thenThrow(notAdmin);
        when(service.update(any(), anyLong(), any())).thenThrow(notAdmin);
        doThrow(notAdmin).when(service).delete(any(), anyLong(), anyBoolean(), anyBoolean());

        mockMvc.perform(get("/api/admin/students").with(signedIn()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(AdminAccess.NOT_AN_ADMIN_MESSAGE));
        mockMvc.perform(get("/api/admin/students/5").with(signedIn())).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/students/5").with(signedIn())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/students/5").with(signedIn())).andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorGetsAPageOfAccounts() throws Exception {
        when(service.search(any(), eq("john"), eq(2), eq(10)))
                .thenReturn(new AdminPageResponse<>(List.of(sarah()), 2, 10, 21, 3));

        mockMvc.perform(get("/api/admin/students?query=john&page=2&size=10").with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].email").value("sarah.johnson@mail.uc.edu"))
                .andExpect(jsonPath("$.items[0].universityName").value("University of Cincinnati"))
                .andExpect(jsonPath("$.items[0].clerkUserId").doesNotExist())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalItems").value(21))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void theListDefaultsToTheFirstPageOfTwenty() throws Exception {
        when(service.search(any(), eq(""), eq(0), eq(20)))
                .thenReturn(new AdminPageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/admin/students").with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void anUpdateReturnsTheSavedAccount_andIgnoresFieldsItDoesNotAccept() throws Exception {
        when(service.update(any(), eq(5L), any())).thenReturn(sarah());

        // email, role and clerkUserId are not fields of the request, so they never arrive.
        mockMvc.perform(put("/api/admin/students/5").with(signedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE.replaceFirst("\\{",
                                "{\"email\":\"attacker@evil.com\",\"role\":\"ADMIN\",\"clerkUserId\":\"user_x\",")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));

        ArgumentCaptor<AdminStudentUpdateRequest> sent = ArgumentCaptor.forClass(AdminStudentUpdateRequest.class);
        verify(service).update(any(), eq(5L), sent.capture());
        assertEquals(AdminStudentUpdateRequest.builder().firstName("Sarah").lastName("Johnson")
                .residentCity("Cincinnati").residentState("OH").universityId(1L).grade("Senior")
                .major("Computer Science").graduationYear(2027).build(), sent.getValue());
    }

    @Test
    void anInvalidUpdate_is400NamingEachField_andNothingIsSaved() throws Exception {
        mockMvc.perform(put("/api/admin/students/5").with(signedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"  ","lastName":"Johnson","residentCity":"Cincinnati",
                                 "residentState":"ohio","universityId":null,"grade":"Senior",
                                 "major":"Computer Science","graduationYear":27}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").value("First name is required"))
                .andExpect(jsonPath("$.fieldErrors.residentState").value("State must be a capitalized 2-letter code"))
                .andExpect(jsonPath("$.fieldErrors.universityId").value("School is required"))
                .andExpect(jsonPath("$.fieldErrors.graduationYear").value("Graduation year must be a four-digit year"))
                .andExpect(jsonPath("$.fieldErrors.lastName").doesNotExist());

        verifyNoInteractions(service);
    }

    @Test
    void aMissingAccount_is404() throws Exception {
        when(service.get(any(), eq(999L))).thenThrow(new SearchNotFoundException(AdminAccountService.NOT_FOUND_MESSAGE));

        mockMvc.perform(get("/api/admin/students/999").with(signedIn()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(AdminAccountService.NOT_FOUND_MESSAGE));
    }

    @Test
    void anIdThatIsNotANumber_is400() throws Exception {
        mockMvc.perform(delete("/api/admin/students/abc").with(signedIn()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'id' is not a valid value."));

        verifyNoInteractions(service);
    }

    @Test
    void aDeleteAnswers204() throws Exception {
        mockMvc.perform(delete("/api/admin/students/5").with(signedIn())).andExpect(status().isNoContent());

        verify(service).delete(any(), eq(5L), eq(false), eq(false));
    }

    @Test
    void aDeleteThatCouldNotFinish_saysSoInsteadOfLookingSuccessful() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, AdminAccountService.CLERK_UNCONFIRMED_MESSAGE))
                .when(service).delete(any(), eq(5L), eq(false), eq(false));

        mockMvc.perform(delete("/api/admin/students/5").with(signedIn()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(AdminAccountService.CLERK_UNCONFIRMED_MESSAGE));
    }
}
