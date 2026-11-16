package com.jonathansoriano.enterprisedevgroupproject.security;

import com.jonathansoriano.enterprisedevgroupproject.security.ClerkAccountDeletion.Outcome;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A profile is deleted on this class's word, so most of what is tested is when it refuses to
 * say "removed". A local HTTP server stands in for Clerk's Backend API.
 */
class ClerkAccountDeletionTest {

    private static final String KEY = "sk_test_example";
    private static final String STUDENT = "user_studentToDelete";
    private static final String ADMIN = "user_admin";

    private HttpServer clerk;
    /** Status to answer per "METHOD id"; anything unlisted answers 404, as Clerk does. */
    private final Map<String, Integer> answers = new HashMap<>();
    private final List<String> calls = new ArrayList<>();
    private final List<String> credentials = new ArrayList<>();

    @BeforeEach
    void startClerk() throws Exception {
        clerk = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        clerk.createContext("/v1/users/", exchange -> {
            String call = exchange.getRequestMethod() + " "
                    + exchange.getRequestURI().getPath().substring("/v1/users/".length());
            calls.add(call);
            credentials.add(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(answers.getOrDefault(call, 404), -1);
            exchange.close();
        });
        clerk.start();
    }

    @AfterEach
    void stopClerk() {
        clerk.stop(0);
    }

    private ClerkAccountDeletion deletion(String secretKey) {
        String url = "http://127.0.0.1:" + clerk.getAddress().getPort();
        return new ClerkAccountDeletion(secretKey, url, new ClerkAccountLookup(secretKey, url));
    }

    @Test
    void clerkDeletesTheAccount_isRemoved() {
        answers.put("DELETE " + STUDENT, 200);

        assertEquals(Outcome.REMOVED, deletion(KEY).delete(STUDENT, ADMIN));

        assertEquals(List.of("DELETE " + STUDENT), calls);
        assertEquals(List.of("Bearer " + KEY), credentials);
    }

    @Test
    void anAccountAlreadyGone_isRemoved_whenClerkKnowsTheAdministrator() {
        // Repeating a delete whose second half failed: Clerk has nothing left to delete.
        answers.put("GET " + ADMIN, 200);

        assertEquals(Outcome.REMOVED, deletion(KEY).delete(STUDENT, ADMIN));
    }

    @Test
    void aKeyFromAnotherClerkInstance_isNotBelieved() {
        // It answers 404 for everyone, the administrator included. Believing it would delete
        // a profile whose real sign-in still exists.
        assertEquals(Outcome.UNCONFIRMED, deletion(KEY).delete(STUDENT, ADMIN));
    }

    @Test
    void noSecretKey_asksNothing() {
        answers.put("DELETE " + STUDENT, 200);

        assertEquals(Outcome.NOT_CONFIGURED, deletion("").delete(STUDENT, ADMIN));
        assertEquals(Outcome.NOT_CONFIGURED, deletion("   ").delete(STUDENT, ADMIN));

        assertTrue(calls.isEmpty(), "without a key there is nothing to ask with");
    }

    @Test
    void anyOtherAnswer_isUnconfirmed() {
        answers.put("GET " + ADMIN, 200);
        for (int status : new int[]{400, 401, 403, 422, 429, 500, 503}) {
            answers.put("DELETE " + STUDENT, status);
            assertEquals(Outcome.UNCONFIRMED, deletion(KEY).delete(STUDENT, ADMIN), "status " + status);
        }
    }

    @Test
    void clerkUnreachable_isUnconfirmed() {
        ClerkAccountDeletion unreachable = deletion(KEY);
        clerk.stop(0);

        assertEquals(Outcome.UNCONFIRMED, unreachable.delete(STUDENT, ADMIN));
    }

    @Test
    void anIdThatIsNotAClerkUserId_isNeverSent() {
        assertEquals(Outcome.UNCONFIRMED, deletion(KEY).delete("../organizations", ADMIN));
        assertEquals(Outcome.UNCONFIRMED, deletion(KEY).delete("user_ok?x=1", ADMIN));
        assertEquals(Outcome.UNCONFIRMED, deletion(KEY).delete(null, ADMIN));

        assertTrue(calls.isEmpty(), "a value that is not a user id must not reach a URL");
    }
}
