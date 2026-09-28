package com.jonathansoriano.enterprisedevgroupproject.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * Clerk's identity sync (ADR-012, S1-04). The only unauthenticated {@code /api} route in
 * the application: there is no session behind a webhook, so the Svix signature over the
 * raw body is the authentication.
 *
 * <h2>What this does and does not create</h2>
 * The story asks for the <em>student</em> row to be created here. It is not possible, and
 * the gap is documented rather than filled with invented data: {@code student} has seven
 * NOT NULL columns a Clerk user event does not carry, and {@code university_id} needs the
 * domain-to-school mapping from S1-03, which does not exist and is blocked on the unvoted
 * D-SCHOOLS decision. So a delivery records the verified identity, and the directory row
 * is still completed by {@code POST /student} — which has bound {@code clerk_user_id}
 * since S1-02, so a profile created after the webhook lands is bound to the same subject.
 *
 * <h2>Ordering, retries and the pending-profile state</h2>
 * Clerk retries until it receives a 2xx and does not guarantee delivery order, so every
 * outcome that is not "we could not authenticate you" answers 2xx: a replay, an event for
 * a type this application ignores, and an event with no usable address are all final states, not failures
 * to retry. Ordering is handled in the upsert rather than here — see
 * {@link ClerkIdentityRepository}.
 *
 * <p>The SPA can reach the application before a delivery lands, and a student can finish
 * their profile before one arrives at all. Both are normal: nothing downstream waits on
 * {@code clerk_identity}, so an identity that is absent, late or duplicated never blocks
 * sign-in or profile completion.
 *
 * <h2>Logging</h2>
 * Invariant 8: no raw body, no signature, and no address is logged. The Clerk subject and
 * the delivery id are safe and are what an operator needs to trace a delivery.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/clerk")
public class ClerkWebhookController {

    private static final String USER_CREATED = "user.created";
    private static final String USER_UPDATED = "user.updated";

    private final SvixSignatureVerifier verifier;
    private final ClerkIdentityRepository identities;
    private final ObjectMapper objectMapper;

    public ClerkWebhookController(SvixSignatureVerifier verifier,
                                  ClerkIdentityRepository identities,
                                  ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.identities = identities;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = SvixSignatureVerifier.ID_HEADER, required = false) String svixId,
            @RequestHeader(value = SvixSignatureVerifier.TIMESTAMP_HEADER, required = false) String svixTimestamp,
            @RequestHeader(value = SvixSignatureVerifier.SIGNATURE_HEADER, required = false) String svixSignature,
            @RequestBody(required = false) byte[] payload) {

        // Before parsing and before touching the database: an unverified body is not data,
        // it is an argument from a stranger.
        verifier.verify(svixId, svixTimestamp, svixSignature, payload);

        JsonNode event = parse(payload);
        String type = event.path("type").asString("");

        if (!USER_CREATED.equals(type) && !USER_UPDATED.equals(type)) {
            // Clerk sends every event type the endpoint is subscribed to. Ignoring the
            // rest with a 2xx stops it retrying something we will never act on.
            log.debug("Ignoring Clerk event type {} (delivery {})", type, svixId);
            return ResponseEntity.noContent().build();
        }

        JsonNode data = event.path("data");
        String clerkUserId = data.path("id").asString("");
        String email = primaryEmailOf(data);

        if (clerkUserId.isBlank() || email == null) {
            // Nothing to key on, and a retry would arrive in the same shape.
            log.warn("Clerk {} delivery {} carried no usable subject or address; ignored.", type, svixId);
            return ResponseEntity.noContent().build();
        }

        int written = identities.record(clerkUserId, email, svixId, eventTimeOf(svixTimestamp));

        // 0 means the row already reflects this delivery or a newer one, which is the
        // expected outcome of a Clerk retry.
        log.info("Clerk {} for subject {} (delivery {}): {}", type, clerkUserId, svixId,
                written == 1 ? "identity recorded" : "already applied, ignored");

        return ResponseEntity.noContent().build();
    }

    private JsonNode parse(byte[] payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (RuntimeException ex) {
            // Signed by Clerk but not JSON we understand. Retrying will not change that,
            // so refuse it outright rather than looping Clerk's delivery queue.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unparseable webhook payload");
        }
    }

    /**
     * Clerk sends every address on the account. The primary one is named by
     * {@code primary_email_address_id}; institutional eligibility is decided on the token
     * at request time by {@code InstitutionalAccessPolicy}, not here.
     *
     * @return the primary address, the only address when no primary is named, or null
     */
    private static String primaryEmailOf(JsonNode data) {
        JsonNode addresses = data.path("email_addresses");
        if (!addresses.isArray() || addresses.isEmpty()) {
            return null;
        }
        String primaryId = data.path("primary_email_address_id").asString("");

        for (JsonNode address : addresses) {
            if (!primaryId.isBlank() && primaryId.equals(address.path("id").asString(""))) {
                return blankToNull(address.path("email_address").asString(""));
            }
        }
        return blankToNull(addresses.get(0).path("email_address").asString(""));
    }

    /**
     * The delivery's own timestamp, already proven recent by the signature check, is used
     * as the event's position in time. Clerk's payload timestamps are not signed, so a
     * forwarded body could otherwise claim any age it liked.
     */
    private static Instant eventTimeOf(String svixTimestamp) {
        return Instant.ofEpochSecond(Long.parseLong(svixTimestamp.trim()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
