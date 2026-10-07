package com.jonathansoriano.enterprisedevgroupproject.messages;

import com.jonathansoriano.enterprisedevgroupproject.identity.CallerIdentity;
import com.jonathansoriano.enterprisedevgroupproject.identity.Party;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.ConversationResponse;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.SendMessageRequest;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.StartConversationRequest;
import com.jonathansoriano.enterprisedevgroupproject.webhook.ClerkIdentityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-012, messaging slice: participant, sender, blocker/blocked and reporter/reported
 * identities follow the Clerk subject, against the real schema and the real resolution
 * path ({@link CallerIdentity#caller}), not hand-built parties.
 */
@SpringBootTest
@Transactional
class MessagingIdentityTest {

    @Autowired private CallerIdentity identity;
    @Autowired private MessagingService messaging;
    @Autowired private ConversationParticipantRepository participants;
    @Autowired private MessageRepository messages;
    @Autowired private BlockedUserRepository blocks;
    @Autowired private UserReportRepository reports;
    @Autowired private UserReportController reportController;
    @Autowired private ClerkIdentityRepository clerkIdentities;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private static Jwt token(String subject, String email) {
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .subject(subject).claim("email", email)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    private ConversationResponse direct(Party from, String toEmail) {
        return messaging.startConversation(StartConversationRequest.builder().recipientEmail(toEmail).build(), from);
    }

    @Test
    @DisplayName("an address change keeps inbox membership, unread state and a block on the student")
    void addressChangeKeepsInboxUnreadAndBlocks() {
        Party aliceOld = identity.caller(token("user_alice_ac", "alice.ac@old.edu"));
        Party bob = identity.caller(token("user_bob_ac", "bob.ac@school.edu"));

        ConversationResponse chat = direct(aliceOld, "bob.ac@school.edu");
        bob = identity.caller(token("user_bob_ac", "bob.ac@school.edu")); // Bob is seen: his row binds
        messaging.sendMessage(chat.getId(), new SendMessageRequest("hi Alice", null), bob);
        messaging.block(bob, "alice.ac@old.edu");
        identity.caller(token("user_alice_ac", "alice.ac@old.edu")); // Alice is seen before renaming

        // Alice changes her address in Clerk. Same subject, new email on the token.
        Party aliceNew = identity.caller(token("user_alice_ac", "alice.ac@new.edu"));
        assertThat(aliceNew.email()).isEqualTo("alice.ac@new.edu");

        assertThat(messaging.listConversations(aliceNew))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.getId()).isEqualTo(chat.getId());
                    assertThat(c.getUnreadCount()).isEqualTo(1);
                });
        assertThat(messaging.listMessages(chat.getId(), aliceNew)).hasSize(1);

        // Bob's block was on her old address; it still holds under the new one.
        final Party renamed = aliceNew;
        assertThatThrownBy(() -> messaging.sendMessage(chat.getId(), new SendMessageRequest("hey", null), renamed))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> direct(renamed, "bob.ac@school.edu"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");

        // Marking read under the new address clears the unread count on the same row.
        messaging.markRead(chat.getId(), aliceNew);
        assertThat(messaging.listConversations(aliceNew).getFirst().getUnreadCount()).isZero();
    }

    @Test
    @DisplayName("a renamed student with a bound profile keeps old-address chats and receives new-address ones")
    void renamedStudentKeepsOldAndReceivesNewAddressChats() {
        // A seeded profile bound to a subject, stored on its original address.
        jdbc.update("UPDATE student SET clerk_user_id = 'user_sarah_rn' WHERE email = 'sarah.johnson@mail.uc.edu'");
        Party bob = identity.caller(token("user_bob_rn", "bob.rn@school.edu"));
        ConversationResponse oldChat = direct(bob, "sarah.johnson@mail.uc.edu");
        Party carl = identity.caller(token("user_carl_rn", "carl.rn@school.edu"));
        ConversationResponse newChat = direct(carl, "sarah.rn@new.edu"); // her current Clerk address

        Party sarah = identity.caller(token("user_sarah_rn", "sarah.rn@new.edu"));
        // New rows use the token's address, the one listings and the web client use.
        assertThat(sarah.email()).isEqualTo("sarah.rn@new.edu");
        assertThat(messaging.listConversations(sarah)).extracting(ConversationResponse::getId)
                .containsExactlyInAnyOrder(oldChat.getId(), newChat.getId());
        assertThat(messaging.sendMessage(newChat.getId(), new SendMessageRequest("hi", null), sarah).getSenderEmail())
                .isEqualTo("sarah.rn@new.edu");
    }

    @Test
    @DisplayName("a token address another known account is recorded on stays reachable but is not bound")
    void addressRecordedForAnotherAccountIsNotBound() {
        clerkIdentities.record("user_other_hd", "taken.hd@school.edu", "msg_hd", Instant.now());
        Party bob = identity.caller(token("user_bob_hd", "bob.hd@school.edu"));
        ConversationResponse chat = direct(bob, "taken.hd@school.edu");

        Party sarah = identity.caller(token("user_sarah_hd", "taken.hd@school.edu"));
        assertThat(participants.findByConversationId(chat.getId()))
                .filteredOn(p -> p.getUserEmail().equals("taken.hd@school.edu"))
                .singleElement().satisfies(p -> assertThat(p.getUserSubject()).isEqualTo("user_other_hd"));
        assertThat(messaging.listConversations(sarah)).isEmpty();
    }

    @Test
    @DisplayName("a block typed with different letter case still binds, so a rename cannot escape it")
    void blockWithDifferentCaseStillFollowsTheSubject() {
        Party alice = identity.caller(token("user_alice_ci", "alice.ci@school.edu"));
        messaging.block(alice, "Bob.CI@School.edu");
        identity.caller(token("user_bob_ci", "bob.ci@school.edu")); // Bob is seen: the block binds

        Party bobRenamed = identity.caller(token("user_bob_ci", "bob.ci@new.edu"));
        assertThatThrownBy(() -> direct(bobRenamed, "alice.ci@school.edu"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    @Test
    @DisplayName("claiming binds one row per (blocker, blocked) pair instead of failing on the unique index")
    void claimDoesNotTripTheSubjectIndex() {
        // Two legacy blocks by the same person, made under two of their addresses.
        blocks.saveAndFlush(BlockedUser.builder().blockerEmail("x1.dd@school.edu").blockerSubject("user_x_dd")
                .blockedEmail("y.dd@school.edu").build());
        blocks.saveAndFlush(BlockedUser.builder().blockerEmail("x2.dd@school.edu").blockerSubject("user_x_dd")
                .blockedEmail("y.dd@school.edu").build());

        identity.caller(token("user_y_dd", "y.dd@school.edu"));
        assertThat(blocks.findAll()).filteredOn(b -> "user_y_dd".equals(b.getBlockedSubject())).hasSize(1);
        Party y = identity.caller(token("user_y_dd", "y.dd@school.edu")); // and again: still no failure
        assertThat(messaging.listConversations(y)).isEmpty();
    }

    @Test
    @DisplayName("unblocking the address shown in the list works after that address moved to another account")
    void unblockByListedAddressAfterRecycling() {
        clerkIdentities.record("user_b_ub", "bob.ub@school.edu", "m1", Instant.now());
        Party alice = identity.caller(token("user_alice_ub", "alice.ub@school.edu"));
        messaging.block(alice, "bob.ub@school.edu");
        // Bob moves away; the address is recycled to Mallory.
        clerkIdentities.record("user_b_ub", "bob.ub2@school.edu", "m2", Instant.now().plusSeconds(1));
        clerkIdentities.record("user_m_ub", "bob.ub@school.edu", "m3", Instant.now().plusSeconds(1));

        assertThat(messaging.listBlocked(alice)).containsExactly("bob.ub@school.edu");
        messaging.unblock(alice, "bob.ub@school.edu");
        assertThat(messaging.listBlocked(alice)).isEmpty();
    }

    @Test
    @DisplayName("blocking a recycled address's new holder answers 409, not 500")
    void blockingRecycledAddressAnswersConflict() {
        clerkIdentities.record("user_b_rb", "bob.rb@school.edu", "m1", Instant.now());
        Party alice = identity.caller(token("user_alice_rb", "alice.rb@school.edu"));
        messaging.block(alice, "bob.rb@school.edu");
        clerkIdentities.record("user_b_rb", "bob.rb2@school.edu", "m2", Instant.now().plusSeconds(1));
        clerkIdentities.record("user_m_rb", "bob.rb@school.edu", "m3", Instant.now().plusSeconds(1));

        assertThatThrownBy(() -> messaging.block(alice, "bob.rb@school.edu"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
    }

    @Test
    @DisplayName("a block on a known identity cannot be escaped by changing address")
    void blockFollowsSubjectRecordedAtBlockTime() {
        // The webhook has recorded Dave's identity, so the block is keyed on his subject at once.
        clerkIdentities.record("user_dave_bl", "dave.bl@old.edu", "msg_1", Instant.now());
        Party erin = identity.caller(token("user_erin_bl", "erin.bl@school.edu"));
        messaging.block(erin, "dave.bl@old.edu");
        assertThat(blocks.findAll()).anySatisfy(b -> assertThat(b.getBlockedSubject()).isEqualTo("user_dave_bl"));

        // Dave has never used messaging; he renames and then tries to reach Erin.
        Party daveNew = identity.caller(token("user_dave_bl", "dave.bl@new.edu"));
        assertThatThrownBy(() -> direct(daveNew, "erin.bl@school.edu"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    @Test
    @DisplayName("non-participants get 403, a conversation that does not exist gets 404")
    void membershipIsCheckedInTheService() {
        Party alice = identity.caller(token("user_alice_mb", "alice.mb@school.edu"));
        ConversationResponse chat = direct(alice, "bob.mb@school.edu");
        Party carol = identity.caller(token("user_carol_mb", "carol.mb@school.edu"));

        assertThatThrownBy(() -> messaging.listMessages(chat.getId(), carol)).hasMessageContaining("403");
        assertThatThrownBy(() -> messaging.sendMessage(chat.getId(), new SendMessageRequest("x", null), carol))
                .hasMessageContaining("403");
        assertThatThrownBy(() -> messaging.markRead(chat.getId(), carol)).hasMessageContaining("403");
        assertThat(messaging.listConversations(carol)).isEmpty();

        assertThatThrownBy(() -> messaging.listMessages(Long.MAX_VALUE, alice)).hasMessageContaining("404");
    }

    @Test
    @DisplayName("a second account on a recycled address does not inherit the first one's conversations")
    void recycledAddressDoesNotEnterConversation() {
        Party original = identity.caller(token("user_orig_rc", "shared.rc@school.edu"));
        ConversationResponse chat = direct(original, "friend.rc@school.edu");

        Party newcomer = identity.caller(token("user_new_rc", "shared.rc@school.edu"));
        assertThat(messaging.listConversations(newcomer)).isEmpty();
        assertThatThrownBy(() -> messaging.listMessages(chat.getId(), newcomer)).hasMessageContaining("403");
    }

    @Test
    @DisplayName("senders and reporters are recorded by subject; a reported student with a known identity is too")
    void sendersAndReportsCarrySubjects() {
        clerkIdentities.record("user_target_rp", "target.rp@school.edu", "msg_2", Instant.now());
        Party alice = identity.caller(token("user_alice_rp", "alice.rp@school.edu"));
        ConversationResponse chat = direct(alice, "target.rp@school.edu");
        messaging.sendMessage(chat.getId(), new SendMessageRequest("hello", null), alice);

        assertThat(messages.findByConversationIdOrderByCreatedAtAsc(chat.getId()))
                .singleElement().satisfies(m -> assertThat(m.getSenderSubject()).isEqualTo("user_alice_rp"));
        assertThat(participants.findByConversationId(chat.getId()))
                .extracting(ConversationParticipant::getUserSubject)
                .containsExactlyInAnyOrder("user_alice_rp", "user_target_rp");

        reportController.report(Map.of("email", "target.rp@school.edu", "reason", "spam"),
                token("user_alice_rp", "alice.rp@school.edu"));
        assertThat(reports.findAll()).anySatisfy(r -> {
            assertThat(r.getReporterSubject()).isEqualTo("user_alice_rp");
            assertThat(r.getReportedSubject()).isEqualTo("user_target_rp");
        });
    }

    @Test
    @DisplayName("(conversation, subject) stays unique under the subject column")
    void participantSubjectIsUniquePerConversation() {
        Party alice = identity.caller(token("user_alice_uq", "alice.uq@school.edu"));
        ConversationResponse chat = direct(alice, "bob.uq@school.edu");
        assertThatThrownBy(() -> participants.saveAndFlush(ConversationParticipant.builder()
                .conversationId(chat.getId()).userEmail("alice.uq@other.edu").userSubject("user_alice_uq").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("(blocker, blocked) stays unique under the subject columns")
    void blockSubjectPairIsUnique() {
        blocks.saveAndFlush(BlockedUser.builder().blockerEmail("a.uq@x.edu").blockerSubject("user_a_uq")
                .blockedEmail("b.uq@x.edu").blockedSubject("user_b_uq").build());
        assertThatThrownBy(() -> blocks.saveAndFlush(BlockedUser.builder().blockerEmail("a.uq@new.edu")
                .blockerSubject("user_a_uq").blockedEmail("b.uq@new.edu").blockedSubject("user_b_uq").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("an address alone never opens a conversation: an unbound row on it stays closed")
    void addressAloneGrantsNoAccess() {
        Party bob = identity.caller(token("user_bob_ad", "bob.ad@school.edu"));
        ConversationResponse chat = direct(bob, "held.ad@school.edu"); // nobody known holds it yet
        // Later Clerk records that address for a different account than the next caller,
        // so the caller's claim skips the row and it stays address-only.
        clerkIdentities.record("user_other_ad", "held.ad@school.edu", "msg_ad", Instant.now());
        Party caller = identity.caller(token("user_caller_ad", "held.ad@school.edu"));

        assertThat(messaging.listConversations(caller)).isEmpty();
        assertThatThrownBy(() -> messaging.listMessages(chat.getId(), caller)).hasMessageContaining("403");
    }
}
