package com.jonathansoriano.enterprisedevgroupproject.domain;

/**
 * The profile field bounds, in one place because two request objects carry the same
 * fields and a column can only be widened once.
 *
 * <p>Each value mirrors a column added in {@code V6__add_student_profile_fields.sql}.
 * The reason they are mirrored at all: a value longer than its column reaches Postgres as
 * a {@code DataIntegrityViolationException}, which the catch-all in
 * {@code ExceptionTranslator} turns into an opaque 500. Bounded here, it is a field-level
 * 400 naming the field — which is what an over-long surname used to do before the V1
 * baseline widened those columns.
 */
public final class ProfileFieldBounds {

    private ProfileFieldBounds() {
    }

    /**
     * Bounds that mirror the columns exactly (V6). Kept as constants so the two request
     * objects cannot drift apart, and so a change to one is a change to the column too.
     */
    public static final int BIO_MAX = 1000;
    public static final int PHOTO_URL_MAX = 255;
    /** A four-digit year. Outside this is a typing error, not a student. */
    public static final int GRADUATION_YEAR_MIN = 1900;
    public static final int GRADUATION_YEAR_MAX = 2100;
    /**
     * https only, and no whitespace, quote or angle bracket. The host allowlist that makes
     * this an *owned* asset arrives with S1-05; this is the same bound listing photos carry.
     */
    public static final String PHOTO_URL_PATTERN = "^https://[^\\s\"'<>`]+$";
}
