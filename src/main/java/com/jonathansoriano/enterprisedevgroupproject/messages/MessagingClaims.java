package com.jonathansoriano.enterprisedevgroupproject.messages;

import com.jonathansoriano.enterprisedevgroupproject.identity.IdentityClaim;
import org.springframework.stereotype.Component;

/**
 * Binds messaging rows (participants, both sides of a block) on a caller's verified address
 * to their subject. Runs on every request: someone can message or block a person by address
 * at any time, before that person's subject is known.
 */
@Component
class MessagingClaims implements IdentityClaim {

    private final ConversationParticipantRepository participants;
    private final BlockedUserRepository blocks;

    MessagingClaims(ConversationParticipantRepository participants, BlockedUserRepository blocks) {
        this.participants = participants;
        this.blocks = blocks;
    }

    @Override
    public void claim(String email, String subject) {
        participants.claim(email, subject);
        blocks.claimAsBlocker(email, subject);
        blocks.claimAsBlocked(email, subject);
    }

    @Override
    public boolean everyRequest() {
        return true;
    }
}
