package com.jonathansoriano.enterprisedevgroupproject.webhook;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Authenticates Clerk's webhook deliveries, which arrive on the only unauthenticated
 * {@code /api} route in the application. There is no bearer token on these requests: the
 * signature over the raw body <em>is</em> the authentication, so anything this class
 * cannot positively verify is refused before the payload is parsed.
 *
 * <p>Clerk signs with Svix. The signed content is
 * {@code <svix-id>.<svix-timestamp>.<raw body>}, HMAC-SHA256 under the endpoint secret,
 * base64-encoded. {@code svix-signature} carries one or more space-separated
 * {@code v1,<signature>} entries, because a rotated secret produces two valid signatures
 * for a while; a match against any entry is a pass.
 *
 * <p>Implemented against the JDK's {@code javax.crypto} rather than Svix's library: the
 * scheme is one HMAC and a constant-time compare, and S1-04 is explicitly not allowed to
 * add a dependency.
 */
@Slf4j
@Component
public class SvixSignatureVerifier {

    public static final String ID_HEADER = "svix-id";
    public static final String TIMESTAMP_HEADER = "svix-timestamp";
    public static final String SIGNATURE_HEADER = "svix-signature";

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SECRET_PREFIX = "whsec_";
    private static final String VERSION_PREFIX = "v1,";

    /**
     * How far a delivery's timestamp may be from this clock. Bounds the window in which
     * a captured request can be replayed against us; Svix signs the timestamp precisely
     * so that receivers can enforce this.
     */
    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    /** Deliberately vague: an attacker probing the endpoint learns nothing from it. */
    private static final String REJECTED = "Webhook signature verification failed";

    /** The decoded endpoint secret, or null when none is configured. */
    private final byte[] secret;

    public SvixSignatureVerifier(@Value("${campusbridge.clerk.webhook-secret:}") String configuredSecret) {
        this.secret = decode(configuredSecret);
    }

    private static byte[] decode(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            return null;
        }
        String base64 = configuredSecret.startsWith(SECRET_PREFIX)
                ? configuredSecret.substring(SECRET_PREFIX.length())
                : configuredSecret;
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            // Never log the value itself, not even a fragment of it (invariant 8).
            log.error("CLERK_WEBHOOK_SECRET is set but is not valid base64 after the "
                    + "'whsec_' prefix; the webhook endpoint will refuse every delivery.");
            return null;
        }
    }

    /**
     * Says out loud whether the endpoint can work, because a missing secret makes it
     * refuse everything and that is otherwise diagnosed as "Clerk is broken".
     */
    @PostConstruct
    void logConfiguration() {
        if (isConfigured()) {
            log.info("Clerk webhook signature verification is active.");
        } else {
            log.warn("CLERK_WEBHOOK_SECRET is not configured: POST /api/webhooks/clerk "
                    + "refuses every delivery. Set it from the Clerk dashboard's endpoint "
                    + "signing secret to enable identity sync.");
        }
    }

    /** Whether an endpoint secret is available. Without one this verifier fails closed. */
    public boolean isConfigured() {
        return secret != null && secret.length > 0;
    }

    /**
     * Passes only for a delivery that is genuinely from Clerk, recent, and whose body is
     * byte-for-byte what was signed.
     *
     * @param id        the {@code svix-id} header
     * @param timestamp the {@code svix-timestamp} header, epoch seconds
     * @param signature the {@code svix-signature} header
     * @param body      the raw request body, exactly as received and not yet parsed
     * @throws ResponseStatusException 401 for any delivery that cannot be verified
     */
    public void verify(String id, String timestamp, String signature, byte[] body) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, REJECTED);
        }
        if (isBlank(id) || isBlank(timestamp) || isBlank(signature) || body == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, REJECTED);
        }
        requireRecent(timestamp);

        byte[] expected = sign(id, timestamp, body);
        for (String candidate : signature.split(" ")) {
            if (!candidate.startsWith(VERSION_PREFIX)) {
                // A future version we do not implement. Skip it rather than guessing.
                continue;
            }
            byte[] provided;
            try {
                provided = Base64.getDecoder().decode(candidate.substring(VERSION_PREFIX.length()));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            // Constant-time: a byte-by-byte compare leaks how much of a forged signature
            // was right, which is enough to build the rest of it.
            if (MessageDigest.isEqual(expected, provided)) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, REJECTED);
    }

    private static void requireRecent(String timestamp) {
        Instant sentAt;
        try {
            sentAt = Instant.ofEpochSecond(Long.parseLong(timestamp.trim()));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, REJECTED);
        }
        if (Duration.between(sentAt, Instant.now()).abs().compareTo(TOLERANCE) > 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, REJECTED);
        }
    }

    /**
     * HMAC over {@code id.timestamp.body}, assembled as bytes rather than by decoding the
     * body to a String first: the signature covers what was sent, and a body that is not
     * well-formed UTF-8 must fail verification, not be silently re-encoded into something
     * that passes.
     */
    private byte[] sign(String id, String timestamp, byte[] body) {
        byte[] prefix = (id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            mac.update(prefix);
            mac.update(body);
            return mac.doFinal();
        } catch (java.security.GeneralSecurityException ex) {
            // HmacSHA256 is required of every JDK, so this is a broken runtime, not input.
            throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
