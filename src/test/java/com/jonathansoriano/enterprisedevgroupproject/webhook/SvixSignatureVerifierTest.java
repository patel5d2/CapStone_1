package com.jonathansoriano.enterprisedevgroupproject.webhook;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The verifier on its own, including the cases that only matter when it fails closed. */
class SvixSignatureVerifierTest {

    // Fabricated local value, base64("campusbridge-test-secret-0123456789").
    private static final String SECRET_BASE64 = "Y2FtcHVzYnJpZGdlLXRlc3Qtc2VjcmV0LTAxMjM0NTY3ODk=";
    private static final String SECRET = "whsec_" + SECRET_BASE64;
    private static final byte[] BODY = "{\"type\":\"user.created\"}".getBytes(StandardCharsets.UTF_8);

    private static String sign(String id, String timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET_BASE64), "HmacSHA256"));
        mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
        mac.update(body);
        return "v1," + Base64.getEncoder().encodeToString(mac.doFinal());
    }

    private static String now() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    @Test
    void acceptsADeliveryItSignedItself() throws Exception {
        SvixSignatureVerifier verifier = new SvixSignatureVerifier(SECRET);
        String timestamp = now();

        assertDoesNotThrow(() -> verifier.verify("msg_1", timestamp, sign("msg_1", timestamp, BODY), BODY));
    }

    @Test
    void acceptsWhenOneOfSeveralSignaturesMatches() throws Exception {
        // Svix sends several during a secret rotation; matching any one of them is a pass.
        SvixSignatureVerifier verifier = new SvixSignatureVerifier(SECRET);
        String timestamp = now();
        String header = "v1,c29tZXRoaW5nRWxzZQ== " + sign("msg_1", timestamp, BODY);

        assertDoesNotThrow(() -> verifier.verify("msg_1", timestamp, header, BODY));
    }

    @Test
    void rejectsASignatureMadeForADifferentDeliveryId() throws Exception {
        // The id is part of the signed content, so a signature cannot be lifted onto
        // another delivery.
        SvixSignatureVerifier verifier = new SvixSignatureVerifier(SECRET);
        String timestamp = now();
        String signature = sign("msg_1", timestamp, BODY);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> verifier.verify("msg_2", timestamp, signature, BODY));

        assertEquals(HttpStatus.UNAUTHORIZED, thrown.getStatusCode());
    }

    @Test
    void withNoSecretConfigured_refusesEverything() throws Exception {
        // Fail closed: an unconfigured endpoint must not accept deliveries it cannot check.
        SvixSignatureVerifier verifier = new SvixSignatureVerifier("");
        String timestamp = now();
        String signature = sign("msg_1", timestamp, BODY);

        assertFalse(verifier.isConfigured());
        assertThrows(ResponseStatusException.class,
                () -> verifier.verify("msg_1", timestamp, signature, BODY));
    }

    @Test
    void withAnUnreadableSecret_refusesEverythingRatherThanFailingToStart() throws Exception {
        // A malformed secret is an operator error. The app still boots — it just cannot
        // accept webhooks — because taking the whole application down would be worse.
        SvixSignatureVerifier verifier = new SvixSignatureVerifier("whsec_not-valid-base64!!");
        String timestamp = now();

        assertFalse(verifier.isConfigured());
        assertThrows(ResponseStatusException.class,
                () -> verifier.verify("msg_1", timestamp, sign("msg_1", timestamp, BODY), BODY));
    }
}
