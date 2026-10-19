package com.jonathansoriano.enterprisedevgroupproject.identity;

/**
 * One side of a stored record — an owner, author, member, sender, blocker or reporter
 * (ADR-012).
 *
 * <p>{@code subject} is the Clerk user ID and is the identity whenever it is known: the
 * caller's always is, because it comes from the verified token. Someone named only by an
 * address — a message recipient, the target of a block or report — has a subject only when
 * a known Clerk identity holds that address; otherwise it is null and the address stands in
 * for them.
 */
public record Party(String subject, String email) {

    /**
     * Whether the caller owns a row. Strictly by subject: the caller's address never grants
     * ownership. Rows written before their owner's subject was known are bound to it when
     * the owner calls in ({@link CallerIdentity#caller}), so by the time this runs they carry
     * the subject too.
     */
    public boolean owns(String rowSubject) {
        return subject != null && subject.equals(rowSubject);
    }

    /**
     * Whether a stored row names this party, for parties that may be known only by address
     * (a recipient, a block or report target). When both sides carry a subject the subject
     * decides, so a renamed student still matches and a second account on a recycled
     * address does not; the address decides only when either side has no subject yet.
     */
    public boolean is(String rowSubject, String rowEmail) {
        if (subject != null && rowSubject != null) {
            return subject.equals(rowSubject);
        }
        return email != null && email.equalsIgnoreCase(rowEmail);
    }

    /** Whether two parties are the same person, for refusing a conversation with oneself. */
    public boolean sameAs(Party other) {
        return email.equalsIgnoreCase(other.email) || (subject != null && subject.equals(other.subject));
    }
}
