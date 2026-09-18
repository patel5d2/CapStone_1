package com.jonathansoriano.enterprisedevgroupproject.webhook;

import com.jonathansoriano.enterprisedevgroupproject.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S1-04's acceptance checks, against the real security filter chain and a disposable
 * PostgreSQL 16: a tampered body cannot write, and a replay converges on one row.
 *
 * <p>Filters are deliberately left ON. The route is the only unauthenticated {@code /api}
 * path in the application, and a test that bypassed the chain would prove the controller
 * works while saying nothing about whether the chain lets an unauthenticated delivery
 * reach it — which is the half most likely to break.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
@ExtendWith(SpringExtension.class)
// A fabricated local signing secret: base64("campusbridge-test-secret-0123456789"). Not a
// Clerk value, not usable anywhere, and the only reason it can live in the repository.
@TestPropertySource(properties =
        "campusbridge.clerk.webhook-secret=whsec_Y2FtcHVzYnJpZGdlLXRlc3Qtc2VjcmV0LTAxMjM0NTY3ODk=")
class ClerkWebhookControllerTest {

    private static final String SECRET_BASE64 = "Y2FtcHVzYnJpZGdlLXRlc3Qtc2VjcmV0LTAxMjM0NTY3ODk=";
    private static final String ENDPOINT = "/api/webhooks/clerk";
    private static final String SUBJECT = "user_2webhookTest";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;

    private static String userEvent(String type, String subject, String email) {
        return """
                {
                  "type": "%s",
                  "data": {
                    "id": "%s",
                    "primary_email_address_id": "idn_primary",
                    "email_addresses": [
                      {"id": "idn_old", "email_address": "old@mail.uc.edu"},
                      {"id": "idn_primary", "email_address": "%s"}
                    ]
                  }
                }
                """.formatted(type, subject, email);
    }

    /** Signs exactly the way Svix does, so the controller is tested against the real scheme. */
    private static String sign(String svixId, String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET_BASE64), "HmacSHA256"));
        mac.update((svixId + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
        mac.update(body.getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(mac.doFinal());
    }

    private static String now() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    private int deliver(String svixId, String timestamp, String signature, String body) throws Exception {
        return mockMvc.perform(post(ENDPOINT)
                        .header(SvixSignatureVerifier.ID_HEADER, svixId)
                        .header(SvixSignatureVerifier.TIMESTAMP_HEADER, timestamp)
                        .header(SvixSignatureVerifier.SIGNATURE_HEADER, signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    private int identityCount() {
        return jdbc.queryForObject("SELECT count(*) FROM clerk_identity WHERE clerk_user_id = ?",
                Integer.class, SUBJECT);
    }

    private String storedEmail() {
        return jdbc.queryForObject("SELECT email FROM clerk_identity WHERE clerk_user_id = ?",
                String.class, SUBJECT);
    }

    @Test
    void validDelivery_recordsTheIdentity() throws Exception {
        String body = userEvent("user.created", SUBJECT, "new.student@mail.uc.edu");
        String timestamp = now();

        assertEquals(204, deliver("msg_1", timestamp, sign("msg_1", timestamp, body), body));

        assertEquals(1, identityCount());
        assertEquals("new.student@mail.uc.edu", storedEmail());
    }

    @Test
    void replayedDelivery_convergesOnOneRow() throws Exception {
        String body = userEvent("user.created", SUBJECT, "new.student@mail.uc.edu");
        String timestamp = now();
        String signature = sign("msg_1", timestamp, body);

        assertEquals(204, deliver("msg_1", timestamp, signature, body));
        // Clerk retries when it does not see a 2xx, so the identical delivery arrives again.
        assertEquals(204, deliver("msg_1", timestamp, signature, body));

        assertEquals(1, identityCount(), "a retry must not create a second identity");
    }

    @Test
    void tamperedBody_isRejectedAndWritesNothing() throws Exception {
        String signed = userEvent("user.created", SUBJECT, "new.student@mail.uc.edu");
        String tampered = userEvent("user.created", SUBJECT, "attacker@mail.uc.edu");
        String timestamp = now();

        // A valid-looking signature — genuinely valid, but for a different body.
        assertEquals(401, deliver("msg_1", timestamp, sign("msg_1", timestamp, signed), tampered));

        assertEquals(0, identityCount(), "an unverified delivery must not reach the database");
    }

    @Test
    void missingSignatureHeaders_areRejected() throws Exception {
        String body = userEvent("user.created", SUBJECT, "new.student@mail.uc.edu");

        assertEquals(401, mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getStatus());

        assertEquals(0, identityCount());
    }

    @Test
    void staleTimestamp_isRejected() throws Exception {
        String body = userEvent("user.created", SUBJECT, "new.student@mail.uc.edu");
        // Correctly signed, but captured an hour ago: outside the replay window.
        String old = String.valueOf(Instant.now().minusSeconds(3600).getEpochSecond());

        assertEquals(401, deliver("msg_1", old, sign("msg_1", old, body), body));

        assertEquals(0, identityCount());
    }

    @Test
    void unsubscribedEventType_isAcceptedButWritesNothing() throws Exception {
        String body = userEvent("session.created", SUBJECT, "new.student@mail.uc.edu");
        String timestamp = now();

        // 2xx so Clerk stops retrying an event this application will never act on.
        assertEquals(204, deliver("msg_1", timestamp, sign("msg_1", timestamp, body), body));

        assertEquals(0, identityCount());
    }

    @Test
    void anOlderEventCannotOverwriteANewerAddress() throws Exception {
        String recent = now();
        String older = String.valueOf(Instant.now().minusSeconds(120).getEpochSecond());

        String current = userEvent("user.updated", SUBJECT, "current@mail.uc.edu");
        assertEquals(204, deliver("msg_2", recent, sign("msg_2", recent, current), current));

        // Clerk does not guarantee order: an earlier event can arrive after a later one.
        String stale = userEvent("user.updated", SUBJECT, "stale@mail.uc.edu");
        assertEquals(204, deliver("msg_1", older, sign("msg_1", older, stale), stale));

        assertEquals("current@mail.uc.edu", storedEmail(),
                "a late-arriving older event must not roll the address back");
    }
}
