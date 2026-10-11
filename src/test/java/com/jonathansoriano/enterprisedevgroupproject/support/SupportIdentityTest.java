package com.jonathansoriano.enterprisedevgroupproject.support;

import com.jonathansoriano.enterprisedevgroupproject.identity.CallerIdentity;
import com.jonathansoriano.enterprisedevgroupproject.identity.Party;
import com.jonathansoriano.enterprisedevgroupproject.support.dto.AnonymousRequestRequest;
import com.jonathansoriano.enterprisedevgroupproject.support.dto.AnonymousRequestResponse;
import com.jonathansoriano.enterprisedevgroupproject.support.dto.SupportResourceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-012, support slice: "my requests" follows the Clerk subject, and the requester's
 * identity — address or subject — never reaches any response.
 */
@SpringBootTest
@Transactional
class SupportIdentityTest {

    @Autowired private CallerIdentity identity;
    @Autowired private SupportService support;
    @Autowired private AnonymousRequestRepository requests;
    @Autowired private ObjectMapper json;

    private static Jwt token(String subject, String email) {
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .subject(subject).claim("email", email)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    private Long ask(Party requester) {
        return support.createRequest(AnonymousRequestRequest.builder()
                .category(SupportCategory.FOOD_PANTRY).description("groceries").build(), requester).getId();
    }

    @Test
    @DisplayName("an address change keeps the requester's own requests listed")
    void addressChangeKeepsMyRequests() {
        Party old = identity.caller(token("user_alice_sp_ac", "alice.sp@old.edu"));
        Long id = ask(old);
        assertThat(requests.findById(id).orElseThrow().getRequesterSubject()).isEqualTo("user_alice_sp_ac");

        Party renamed = identity.caller(token("user_alice_sp_ac", "alice.sp@new.edu"));
        assertThat(support.listMine(renamed)).extracting(AnonymousRequestResponse::getId).containsExactly(id);
    }

    @Test
    @DisplayName("another student never sees whose request it is; any student may fulfill; a missing one is 404")
    void otherStudentSeesNoRequesterAndMissingIsNotFound() {
        Party alice = identity.caller(token("user_alice_sp_ot", "alice.ot@school.edu"));
        Party bob = identity.caller(token("user_bob_sp_ot", "bob.ot@school.edu"));
        Long id = ask(alice);

        assertThat(support.listMine(bob)).extracting(AnonymousRequestResponse::getId).doesNotContain(id);
        String publicJson = json.writeValueAsString(support.listPublicRequests(null, null));
        String bobsJson = json.writeValueAsString(support.listMine(bob));
        for (String body : new String[]{publicJson, bobsJson}) {
            assertThat(body).doesNotContainIgnoringCase("alice.ot@school.edu")
                    .doesNotContain("user_alice_sp_ot")
                    .doesNotContainIgnoringCase("requester");
        }
        assertThat(publicJson).contains("\"id\":" + id);

        support.fulfill(id); // the controller only requires a signed-in caller
        assertThat(support.listMine(alice)).singleElement()
                .satisfies(r -> assertThat(r.getStatus()).isEqualTo(RequestStatus.FULFILLED));
        assertThatThrownBy(() -> support.fulfill(Long.MAX_VALUE)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("no support response type has a field that could identify the requester")
    void responseTypesCarryNoRequesterField() {
        for (Class<?> type : new Class<?>[]{AnonymousRequestResponse.class, SupportResourceResponse.class}) {
            assertThat(Arrays.stream(type.getDeclaredFields()).map(Field::getName).map(String::toLowerCase))
                    .as(type.getSimpleName())
                    .noneMatch(name -> name.contains("email") || name.contains("subject")
                            || name.contains("requester") || name.contains("clerk"));
        }
    }

    @Test
    @DisplayName("a new account on a recycled address does not see the earlier holder's requests")
    void recycledAddressDoesNotInheritRequests() {
        Party first = identity.caller(token("user_first_sp_rc", "shared.sp@school.edu"));
        ask(first);

        Party second = identity.caller(token("user_second_sp_rc", "shared.sp@school.edu"));
        assertThat(support.listMine(second)).isEmpty();
    }

    @Test
    @DisplayName("a legacy address-only request is claimed by the caller and then listed as theirs")
    void legacyAddressOnlyRequestIsClaimed() {
        AnonymousRequest legacy = requests.save(AnonymousRequest.builder().requesterEmail("Legacy.SP@School.edu")
                .category(SupportCategory.OTHER).description("old").build());

        Party caller = identity.caller(token("user_legacy_sp", "legacy.sp@school.edu"));
        assertThat(requests.findById(legacy.getId()).orElseThrow().getRequesterSubject()).isEqualTo("user_legacy_sp");
        assertThat(support.listMine(caller)).extracting(AnonymousRequestResponse::getId).containsExactly(legacy.getId());
    }
}
