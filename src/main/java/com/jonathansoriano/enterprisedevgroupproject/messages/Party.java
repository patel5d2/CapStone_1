package com.jonathansoriano.enterprisedevgroupproject.messages;

/**
 * One side of a conversation, message, block or report (ADR-012, messaging slice).
 *
 * <p>{@code subject} is the Clerk user ID and is the identity whenever it is known: the
 * caller's always is, because it comes from the verified token. Someone named only by an
 * address — a recipient, a seller, the target of a block — has a subject only when a known
 * Clerk identity holds that address; otherwise it is null and the address stands in until
 * that person is seen (identity-backfill.md rule 5).
 */
public record Party(String subject, String email) {

    /**
     * Whether a stored row's identity is this party. When both sides carry a subject the
     * subject decides, so a renamed student still matches rows written under their old
     * address and a second account on a recycled address does not. The address decides
     * only when either side has no subject yet.
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
