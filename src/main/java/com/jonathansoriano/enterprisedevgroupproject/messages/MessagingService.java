package com.jonathansoriano.enterprisedevgroupproject.messages;

import com.jonathansoriano.enterprisedevgroupproject.identity.CallerIdentity;
import com.jonathansoriano.enterprisedevgroupproject.identity.Party;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.ListingRepository;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.ConversationResponse;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.MessageResponse;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.SendMessageRequest;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.StartConversationRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Conversations, messages and blocks, keyed on the Clerk subject with the address as the
 * stand-in for anyone not yet identified (ADR-012, messaging slice). Every caller is a
 * {@link Party} from {@link CallerIdentity#caller}; stored rows are matched with
 * {@link Party#is}, so ownership follows the subject through an address change.
 */
@Service
public class MessagingService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final ListingRepository listingRepository;
    private final CallerIdentity identity;

    public MessagingService(ConversationRepository conversationRepository,
                             ConversationParticipantRepository participantRepository,
                             MessageRepository messageRepository,
                             BlockedUserRepository blockedUserRepository,
                             ListingRepository listingRepository,
                             CallerIdentity identity) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.blockedUserRepository = blockedUserRepository;
        this.listingRepository = listingRepository;
        this.identity = identity;
    }

    @Transactional
    public ConversationResponse startConversation(StartConversationRequest request, Party requester) {
        Party recipient;
        Long listingId = null;
        ConversationType type;

        if (request.getListingId() != null) {
            var listing = listingRepository.findById(request.getListingId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
            // The seller is whoever the listing records, not whoever holds its address now.
            recipient = listing.getSellerSubject() != null
                    ? new Party(listing.getSellerSubject(), listing.getSellerEmail())
                    : identity.byAddress(listing.getSellerEmail());
            listingId = listing.getId();
            type = ConversationType.MARKETPLACE;
        } else if (request.getRecipientEmail() != null && !request.getRecipientEmail().isBlank()) {
            recipient = identity.byAddress(request.getRecipientEmail());
            type = ConversationType.DIRECT;
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Either listingId or recipientEmail is required");
        }

        if (recipient.sameAs(requester)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot start a conversation with yourself");
        }
        if (isBlocked(recipient, requester) || isBlocked(requester, recipient)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Messaging is blocked between these users");
        }

        Long existingConversationId = findExistingConversation(listingId, requester, recipient);
        Long conversationId;
        if (existingConversationId != null) {
            conversationId = existingConversationId;
        } else {
            Conversation conversation = conversationRepository.save(Conversation.builder()
                    .type(type)
                    .listingId(listingId)
                    .build());
            conversationId = conversation.getId();
            participantRepository.save(participantRow(conversationId, requester));
            participantRepository.save(participantRow(conversationId, recipient));
        }

        return toConversationResponse(conversationRepository.findById(conversationId).orElseThrow(), requester);
    }

    public List<ConversationResponse> listConversations(Party caller) {
        return participationsOf(caller).stream()
                .map(participant -> conversationRepository.findById(participant.getConversationId()).orElseThrow())
                .map(conversation -> toConversationResponse(conversation, caller))
                .collect(Collectors.toList());
    }

    public List<MessageResponse> listMessages(Long conversationId, Party caller) {
        requireParticipant(conversationId, caller);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public MessageResponse sendMessage(Long conversationId, SendMessageRequest request, Party sender) {
        requireParticipant(conversationId, sender);
        List<ConversationParticipant> others = participantRepository.findByConversationId(conversationId).stream()
                .filter(participant -> !isCaller(participant, sender))
                .toList();

        for (ConversationParticipant participant : others) {
            if (isBlocked(partyOf(participant), sender)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The recipient has blocked you");
            }
        }

        Message message = messageRepository.save(Message.builder()
                .conversationId(conversationId)
                .senderEmail(sender.email())
                .senderSubject(sender.subject())
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .build());

        for (ConversationParticipant participant : others) {
            participant.setUnreadCount(participant.getUnreadCount() + 1);
            participantRepository.save(participant);
        }

        return toMessageResponse(message);
    }

    @Transactional
    public void markRead(Long conversationId, Party caller) {
        ConversationParticipant participant = requireParticipant(conversationId, caller);
        participant.setUnreadCount(0);
        participant.setLastReadAt(Instant.now());
        participantRepository.save(participant);
    }

    public void block(Party blocker, String blockedEmail) {
        Party blocked = identity.byAddress(blockedEmail);
        if (isBlocked(blocker, blocked)) {
            return;
        }
        try {
            blockedUserRepository.saveAndFlush(BlockedUser.builder()
                    .blockerEmail(blocker.email()).blockerSubject(blocker.subject())
                    .blockedEmail(blocked.email()).blockedSubject(blocked.subject())
                    .build());
        } catch (DataIntegrityViolationException collision) {
            // An earlier block of yours sits on this same address but belongs to the account
            // that held it before (a recycled address). uk_blocked_user still keys on the
            // address pair, so a second row cannot exist yet — say so instead of a 500.
            // ponytail: goes away when the email columns and their constraint do (S1-12).
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have a block on this address from a previous account. "
                            + "Remove it from your blocked list, then block again.");
        }
    }

    /**
     * Removes the caller's blocks on this person, and on the address as listed: the blocked
     * list shows addresses, and one may now belong to someone other than the account blocked.
     */
    @Transactional
    public void unblock(Party blocker, String blockedEmail) {
        Party blocked = identity.byAddress(blockedEmail);
        blockedUserRepository.deleteAll(blocksBy(blocker).stream()
                .filter(block -> blocked.is(block.getBlockedSubject(), block.getBlockedEmail())
                        || blockedEmail.equalsIgnoreCase(block.getBlockedEmail()))
                .toList());
    }

    public List<String> listBlocked(Party blocker) {
        return blocksBy(blocker).stream()
                .map(BlockedUser::getBlockedEmail)
                .collect(Collectors.toList());
    }

    /** Whether {@code blocker} has blocked {@code target}, compared on subjects where known. */
    private boolean isBlocked(Party blocker, Party target) {
        return blocksBy(blocker).stream()
                .anyMatch(block -> target.is(block.getBlockedSubject(), block.getBlockedEmail()));
    }

    private List<BlockedUser> blocksBy(Party blocker) {
        return blockedUserRepository.findCandidatesByBlocker(blocker.subject(), blocker.email()).stream()
                .filter(block -> blocker.is(block.getBlockerSubject(), block.getBlockerEmail()))
                .toList();
    }

    private List<ConversationParticipant> participationsOf(Party party) {
        return participantRepository.findCandidates(party.subject(), party.email()).stream()
                .filter(participant -> isCaller(participant, party))
                .toList();
    }

    private Long findExistingConversation(Long listingId, Party userA, Party userB) {
        for (ConversationParticipant participation : participationsOf(userA)) {
            Conversation conversation = conversationRepository.findById(participation.getConversationId()).orElse(null);
            if (conversation == null) {
                continue;
            }
            boolean sameListing = listingId == null
                    ? conversation.getListingId() == null
                    : listingId.equals(conversation.getListingId());
            if (!sameListing) {
                continue;
            }
            boolean hasOtherUser = participantRepository.findByConversationId(conversation.getId()).stream()
                    .filter(p -> !p.getId().equals(participation.getId()))
                    .anyMatch(p -> isCaller(p, userB));
            if (hasOtherUser) {
                return conversation.getId();
            }
        }
        return null;
    }

    /**
     * Membership is decided here, in the service, by comparing the loaded participants with
     * the caller (invariant 2): a conversation that does not exist is 404, one that exists
     * without the caller in it is 403.
     */
    private ConversationParticipant requireParticipant(Long conversationId, Party caller) {
        if (!conversationRepository.existsById(conversationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        return participantRepository.findByConversationId(conversationId).stream()
                .filter(participant -> isCaller(participant, caller))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not part of this conversation"));
    }

    private static boolean isCaller(ConversationParticipant participant, Party party) {
        return party.is(participant.getUserSubject(), participant.getUserEmail());
    }

    private static Party partyOf(ConversationParticipant participant) {
        return new Party(participant.getUserSubject(), participant.getUserEmail());
    }

    private static ConversationParticipant participantRow(Long conversationId, Party party) {
        return ConversationParticipant.builder()
                .conversationId(conversationId)
                .userEmail(party.email())
                .userSubject(party.subject())
                .unreadCount(0)
                .build();
    }

    private ConversationResponse toConversationResponse(Conversation conversation, Party caller) {
        List<ConversationParticipant> participants = participantRepository.findByConversationId(conversation.getId());
        int unread = participants.stream()
                .filter(p -> isCaller(p, caller))
                .findFirst()
                .map(ConversationParticipant::getUnreadCount)
                .orElse(0);

        return ConversationResponse.builder()
                .id(conversation.getId())
                .type(conversation.getType())
                .listingId(conversation.getListingId())
                .participantEmails(participants.stream().map(ConversationParticipant::getUserEmail).collect(Collectors.toList()))
                .unreadCount(unread)
                .createdAt(conversation.getCreatedAt())
                .build();
    }

    private MessageResponse toMessageResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderEmail(message.getSenderEmail())
                .content(message.getContent())
                .imageUrl(message.getImageUrl())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
