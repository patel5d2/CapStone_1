package com.jonathansoriano.enterprisedevgroupproject.identity;

/**
 * One feature's step for binding its address-only rows to their owner's Clerk subject
 * (ADR-012). {@link CallerIdentity} runs every registered claim for the caller's verified
 * addresses before any feature code sees the caller, so ownership checks can compare
 * subjects only.
 */
public interface IdentityClaim {

    /** Binds rows still keyed only on {@code email} to {@code subject}. Must be idempotent. */
    void claim(String email, String subject);

    /**
     * Whether new address-only rows for a person can appear after they were first seen.
     * Messaging can (someone messages or blocks them by address), so it claims on every
     * request. Features whose rows are only ever written by their owner — who now always
     * carries a subject — need claiming once per subject.
     */
    default boolean everyRequest() {
        return false;
    }
}
