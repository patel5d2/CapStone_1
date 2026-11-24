package com.jonathansoriano.enterprisedevgroupproject.admin;

import com.jonathansoriano.enterprisedevgroupproject.domain.UserRequest;
import com.jonathansoriano.enterprisedevgroupproject.dto.UserDto;
import com.jonathansoriano.enterprisedevgroupproject.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Administrator accounts are created from configuration, and from nowhere else. */
class AdminAccountSeederTest {

    private final UserRepository users = mock(UserRepository.class);

    private void seed(String configured) {
        new AdminAccountSeeder(users, configured).run();
    }

    @Test
    void nothingConfigured_createsNobody() {
        seed("");
        seed("  ,  , ");

        verifyNoInteractions(users);
    }

    @Test
    void aNewAddress_getsADedicatedAdministratorAccount() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());

        seed(" Dean@UC.edu ");

        ArgumentCaptor<UserRequest> created = ArgumentCaptor.forClass(UserRequest.class);
        verify(users).insertNewUser(created.capture());
        assertEquals("dean@uc.edu", created.getValue().getEmail(), "stored the way Clerk reports it");
        assertEquals("ADMIN", created.getValue().getRole());
        assertNull(created.getValue().getPassword(), "Clerk owns the credential");
    }

    @Test
    void anExistingStudent_isPromotedInPlace() {
        when(users.findByEmail("student@mail.uc.edu")).thenReturn(
                Optional.of(UserDto.builder().id(7L).email("student@mail.uc.edu").role("USER").build()));

        seed("student@mail.uc.edu");

        verify(users).updateRole(7L, "ADMIN");
        verify(users, never()).insertNewUser(any());
    }

    @Test
    void anExistingAdministrator_isLeftAlone() {
        when(users.findByEmail("dean@uc.edu")).thenReturn(
                Optional.of(UserDto.builder().id(3L).email("dean@uc.edu").role("ADMIN").build()));

        seed("dean@uc.edu");

        verify(users, never()).insertNewUser(any());
        verify(users, never()).updateRole(anyLong(), anyString());
    }

    @Test
    void severalAddresses_areEachHandledOnce() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());

        seed("dean@uc.edu, registrar@xavier.edu,DEAN@uc.edu");

        verify(users, times(2)).insertNewUser(any());
    }

    @Test
    void somethingThatIsNotAnAddress_isIgnored() {
        seed("not-an-address");

        verifyNoInteractions(users);
    }
}
