package com.jonathansoriano.enterprisedevgroupproject.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Removes an account's sign-in from Clerk, for an administrator deleting a student (#113).
 *
 * <p>Deleting only the directory profile would leave the student able to sign in and make a
 * new one, so "deleted" would not mean they are gone. Clerk owns the credential, which makes
 * Clerk the only place a sign-in can be taken away.
 *
 * <p><b>Reports only what Clerk confirmed.</b> The caller deletes the profile on
 * {@link Outcome#REMOVED} alone. No secret key, an unreachable Clerk and any unexpected
 * answer all come back as something else, so a failed deletion is never shown as a success.
 */
@Slf4j
@Component
public class ClerkAccountDeletion {

    public enum Outcome {
        /** Clerk deleted the account, or confirmed it was already gone. */
        REMOVED,
        /** There is no secret key, so Clerk was not asked. */
        NOT_CONFIGURED,
        /** Clerk was asked and did not confirm; the account may still exist. */
        UNCONFIRMED
    }

    /** Clerk user ids as they appear in a token's {@code sub}. Checked before one is put in a URL. */
    private static final Pattern USER_ID = Pattern.compile("user_[A-Za-z0-9]{1,64}");

    private final String secretKey;
    private final String apiUrl;
    private final ClerkAccountLookup lookup;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public ClerkAccountDeletion(@Value("${campusbridge.clerk.secret-key:}") String secretKey,
                                @Value("${campusbridge.clerk.api-url:https://api.clerk.com}") String apiUrl,
                                ClerkAccountLookup lookup) {
        this.secretKey = secretKey.trim();
        this.apiUrl = apiUrl;
        this.lookup = lookup;
    }

    /**
     * Deletes one Clerk account.
     *
     * <p>A 404 is "already gone" only when {@link ClerkAccountLookup#isDeleted} agrees, which
     * also requires Clerk to recognise the caller. A secret key from another Clerk instance
     * answers 404 for every account here; without that second check it would read as "already
     * deleted" and a profile would be removed while its real sign-in lived on.
     *
     * @param clerkUserId the account to delete
     * @param caller      the subject of the administrator's verified token
     */
    public Outcome delete(String clerkUserId, String caller) {
        if (secretKey.isEmpty()) {
            return Outcome.NOT_CONFIGURED;
        }
        if (clerkUserId == null || !USER_ID.matcher(clerkUserId).matches()) {
            return Outcome.UNCONFIRMED;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + "/v1/users/" + clerkUserId))
                    .header("Authorization", "Bearer " + secretKey)
                    .timeout(Duration.ofSeconds(5))
                    .DELETE()
                    .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) {
                return Outcome.REMOVED;
            }
            if (status == 404) {
                return lookup.isDeleted(clerkUserId, caller) ? Outcome.REMOVED : Outcome.UNCONFIRMED;
            }
            // 401 is the one worth reading: the secret key is wrong or was rotated.
            log.warn("Clerk answered {} when asked to delete account {}; nothing was deleted.", status, clerkUserId);
            return Outcome.UNCONFIRMED;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Outcome.UNCONFIRMED;
        } catch (Exception unreachable) {
            // Invariant 8: the exception may quote the request, so only its type is logged.
            log.warn("Could not reach Clerk to delete account {} ({}); nothing was deleted.",
                    clerkUserId, unreachable.getClass().getSimpleName());
            return Outcome.UNCONFIRMED;
        }
    }
}
