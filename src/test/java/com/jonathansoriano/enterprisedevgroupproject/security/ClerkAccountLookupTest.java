package com.jonathansoriano.enterprisedevgroupproject.security;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A profile is deleted on this class's word, so what is tested is mostly when it says no:
 * every answer short of Clerk's own "that account does not exist, and yours does".
 * A local HTTP server stands in for Clerk's Backend API.
 */
class ClerkAccountLookupTest {

    private static final String KEY = "sk_test_example";
    private static final String DELETED = "user_deletedOwner";
    private static final String CALLER = "user_caller";

    private HttpServer clerk;
    /** Status to answer per user id; anything unlisted answers 404, as Clerk does. */
    private final Map<String, Integer> answers = new HashMap<>();
    private final List<String> asked = new ArrayList<>();
    private final List<String> credentials = new ArrayList<>();

    @BeforeEach
    void startClerk() throws Exception {
        clerk = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        clerk.createContext("/v1/users/", exchange -> {
            String id = exchange.getRequestURI().getPath().substring("/v1/users/".length());
            asked.add(id);
            credentials.add(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(answers.getOrDefault(id, 404), -1);
            exchange.close();
        });
        clerk.start();
    }

    @AfterEach
    void stopClerk() {
        clerk.stop(0);
    }

    private ClerkAccountLookup lookup(String secretKey) {
        return new ClerkAccountLookup(secretKey, "http://127.0.0.1:" + clerk.getAddress().getPort());
    }

    @Test
    void ownerGoneAndCallerKnown_isDeleted() {
        answers.put(CALLER, 200);

        assertTrue(lookup(KEY).isDeleted(DELETED, CALLER));

        assertEquals(List.of(DELETED, CALLER), asked);
        assertEquals(List.of("Bearer " + KEY, "Bearer " + KEY), credentials);
    }

    @Test
    void ownerStillExists_isNotDeleted() {
        // The owner changed their address in Clerk; the profile is still theirs.
        answers.put(DELETED, 200);
        answers.put(CALLER, 200);

        assertFalse(lookup(KEY).isDeleted(DELETED, CALLER));
    }

    @Test
    void aKeyThatCannotFindTheCaller_isNotBelieved() {
        // A secret key from another Clerk instance answers 404 for everyone here. The
        // caller's token was just verified, so "no such caller" means the wrong key.
        assertFalse(lookup(KEY).isDeleted(DELETED, CALLER));
    }

    @Test
    void noSecretKey_asksNothing() {
        answers.put(CALLER, 200);

        assertFalse(lookup("").isDeleted(DELETED, CALLER));
        assertFalse(lookup("   ").isDeleted(DELETED, CALLER));

        assertTrue(asked.isEmpty(), "without a key there is nothing to ask with");
    }

    @Test
    void anyOtherAnswer_isNotDeleted() {
        answers.put(CALLER, 200);
        for (int status : new int[]{401, 403, 429, 500, 503}) {
            answers.put(DELETED, status);
            assertFalse(lookup(KEY).isDeleted(DELETED, CALLER), "status " + status);
        }
    }

    @Test
    void clerkUnreachable_isNotDeleted() {
        ClerkAccountLookup unreachable = lookup(KEY);
        clerk.stop(0);

        assertFalse(unreachable.isDeleted(DELETED, CALLER));
    }

    @Test
    void anIdThatIsNotAClerkUserId_isNeverSent() {
        answers.put(CALLER, 200);

        assertFalse(lookup(KEY).isDeleted("../organizations", CALLER));
        assertFalse(lookup(KEY).isDeleted("user_ok?x=1", CALLER));
        assertFalse(lookup(KEY).isDeleted(null, CALLER));

        assertTrue(asked.isEmpty(), "a value that is not a user id must not reach a URL");
    }
}
