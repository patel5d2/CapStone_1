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
 * Asks Clerk whether an account still exists.
 *
 * <p>One question, for one situation: a verified token presents the address of a profile
 * that is bound to a <em>different</em> Clerk subject ({@code StudentIdentityService}). Clerk
 * lets an address belong to one account at a time, so the bound account no longer holds it —
 * but that is true both when the account was deleted and when its owner merely changed
 * address. Only the first leaves a profile nobody owns, and only Clerk can tell them apart.
 *
 * <p><b>Fails towards "still exists".</b> Without a secret key, on any error, and on any
 * answer other than Clerk's own 404, the account is treated as alive and the caller is
 * refused as before. A profile is only ever given up on positive evidence.
 */
@Slf4j
@Component
public class ClerkAccountLookup {

    /** Clerk user ids as they appear in a token's {@code sub}. Checked before one is put in a URL. */
    private static final Pattern USER_ID = Pattern.compile("user_[A-Za-z0-9]{1,64}");

    private final String secretKey;
    private final String apiUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public ClerkAccountLookup(@Value("${campusbridge.clerk.secret-key:}") String secretKey,
                              @Value("${campusbridge.clerk.api-url:https://api.clerk.com}") String apiUrl) {
        this.secretKey = secretKey.trim();
        this.apiUrl = apiUrl;
    }

    /**
     * True only when Clerk says {@code formerOwner} does not exist <em>and</em> says the
     * caller does.
     *
     * <p>The second half is what makes the first trustworthy. A secret key from another
     * Clerk instance answers 404 for every account here, and would otherwise read as "all
     * of them were deleted". The caller's subject comes from a token this application has
     * just verified against its own instance, so a key that cannot find <em>them</em> is the
     * wrong key, and nothing it says is believed.
     *
     * @param formerOwner the subject a profile is bound to
     * @param caller      the subject of the verified token making the request
     */
    public boolean isDeleted(String formerOwner, String caller) {
        if (secretKey.isEmpty()) {
            return false;
        }
        return statusOf(formerOwner) == 404 && statusOf(caller) == 200;
    }

    /** Clerk's HTTP status for one account, or -1 when there is no answer to go on. */
    private int statusOf(String clerkUserId) {
        if (clerkUserId == null || !USER_ID.matcher(clerkUserId).matches()) {
            return -1;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + "/v1/users/" + clerkUserId))
                    .header("Authorization", "Bearer " + secretKey)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status != 200 && status != 404) {
                // 401 is the one worth reading: the secret key is wrong or was rotated.
                log.warn("Clerk answered {} when asked whether an account exists; treating it as existing.", status);
            }
            return status;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (Exception unreachable) {
            // Invariant 8: the exception may quote the request, so only its type is logged.
            log.warn("Could not reach Clerk to ask whether an account exists ({}); treating it as existing.",
                    unreachable.getClass().getSimpleName());
            return -1;
        }
    }
}
