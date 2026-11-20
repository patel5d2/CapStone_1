package com.jonathansoriano.enterprisedevgroupproject.admin;

import com.jonathansoriano.enterprisedevgroupproject.config.SecurityConfig;
import com.jonathansoriano.enterprisedevgroupproject.security.AccountBans;
import com.jonathansoriano.enterprisedevgroupproject.security.ClerkJwtAuthenticationConverter;
import com.jonathansoriano.enterprisedevgroupproject.security.InstitutionalAccessDeniedHandler;
import com.jonathansoriano.enterprisedevgroupproject.security.InstitutionalAccessPolicy;
import com.jonathansoriano.enterprisedevgroupproject.support.RequestStatus;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP face of moderation, behind the real security filter chain. What each action does
 * is {@code AdminModerationServiceTest}'s subject; this is about who reaches the service and
 * how a bad request or a refusal comes back.
 */
@WebMvcTest(controllers = AdminModerationController.class)
@Import({SecurityConfig.class, InstitutionalAccessPolicy.class,
        ClerkJwtAuthenticationConverter.class, InstitutionalAccessDeniedHandler.class})
class AdminModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminModerationService service;
    @MockitoBean
    private AccountBans accountBans;

    private static RequestPostProcessor signedIn() {
        return jwt().jwt(j -> j.subject("user_caller").claim("email", "caller@mail.uc.edu"))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    @Test
    void noToken_is401_andNothingIsRemoved() throws Exception {
        for (String path : new String[]{"/api/admin/listings/5", "/api/admin/posts/5", "/api/admin/comments/5",
                "/api/admin/groups/5", "/api/admin/events/5", "/api/admin/support/requests/5",
                "/api/admin/support/resources/5", "/api/admin/listings/5/reports"}) {
            mockMvc.perform(delete(path)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/admin/listings")).andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void aStudentWhoIsNotAnAdministrator_gets403WithAReason() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, AdminAccess.NOT_AN_ADMIN_MESSAGE))
                .when(service).deleteListing(any(), anyLong());

        mockMvc.perform(delete("/api/admin/listings/5").with(signedIn()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AdminAccess.NOT_AN_ADMIN_MESSAGE));
    }

    @Test
    void aBannedAccountIsToldItIsBanned() throws Exception {
        when(accountBans.isBanned(any())).thenReturn(true);

        // No ROLE_STUDENT: what the converter leaves a banned token with.
        mockMvc.perform(get("/api/admin/listings").with(jwt().jwt(j -> j.claim("email", "caller@mail.uc.edu"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AccountBans.BANNED_MESSAGE));

        verifyNoInteractions(service);
    }

    @Test
    void removalsAnswer204_andSearchesPassTheirFiltersThrough() throws Exception {
        mockMvc.perform(delete("/api/admin/posts/7").with(signedIn())).andExpect(status().isNoContent());
        verify(service).deletePost(any(), eq(7L));

        mockMvc.perform(get("/api/admin/listings?query=tickets&reportedOnly=true&page=1&size=5").with(signedIn()))
                .andExpect(status().isOk());
        verify(service).listings(any(), eq("tickets"), eq(true), eq(1), eq(5));

        mockMvc.perform(get("/api/admin/support/requests?status=CLOSED").with(signedIn())).andExpect(status().isOk());
        verify(service).supportRequests(any(), eq(RequestStatus.CLOSED), eq(0), eq(20));
    }

    @Test
    void aStatusThatDoesNotExist_is400_notAServerError() throws Exception {
        mockMvc.perform(get("/api/admin/support/requests?status=BOGUS").with(signedIn()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/support/requests/3/status").with(signedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request could not be read. Check the values and try again."));
        mockMvc.perform(put("/api/admin/support/requests/3/status").with(signedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").value("Status is required"));

        verifyNoInteractions(service);
    }

    @Test
    void anIncompleteSupportResource_is400NamingEachField() throws Exception {
        mockMvc.perform(post("/api/admin/support/resources").with(signedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolId\":null,\"category\":\"COUNSELING\",\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.schoolId").value("School is required"))
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"));

        verifyNoInteractions(service);
    }
}
