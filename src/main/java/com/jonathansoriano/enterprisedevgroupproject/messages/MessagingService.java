package com.jonathansoriano.enterprisedevgroupproject.messages;

import com.jonathansoriano.enterprisedevgroupproject.marketplace.ListingRepository;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.ConversationResponse;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.MessageResponse;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.SendMessageRequest;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.StartConversationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessagingService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final ListingRepository listingRepository;

    public MessagingService(ConversationRepository conversationRepository,
                             ConversationParticipantRepository participantRepository,
                             MessageRepository messageRepository,
                             BlockedUserRepository blockedUserRepository,
                             ListingRepository listingRepository) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.blockedUserRepository = blockedUserRepository;
        this.listingRepository = listingRepository;
    }

    @Transactional
    public ConversationResponse startConversation(StartConversationRequest request, String requesterEmail) {
        String recipientEmail;
        Long listingId = null;
        ConversationType type;

        if (request.getListingId() != null) {
            var listing = listingRepository.findById(request.getListingId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
            recipientEmail = listing.getSellerEmail();
            listingId = listing.getId();
            type = ConversationType.MARKETPLACE;
        } else if (request.getRecipientEmail() != null && !request.getRecipientEmail().isBlank()) {
            recipientEmail = request.getRecipientEmail();
            type = ConversationType.DIRECT;
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Either listingId or recipientEmail is required");
        }

        if (recipientEmail.equalsIgnoreCase(requesterEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot start a conversation with yourself");
        }
        if (blockedUserRepository.existsByBlockerEmailAndBlockedEmail(recipientEmail, requesterEmail)
                || blockedUserRepository.existsByBlockerEmailAndBlockedEmail(requesterEmail, recipientEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Messaging is blocked between these users");
        }

        Long existingConversationId = findExistingConversation(listingId, requesterEmail, recipientEmail);
        Long conversationId;
        if (existingConversationId != null) {
            conversationId = existingConversationId;
        } else {
            Conversation conversation = conversationRepository.save(Conversation.builder()
                    .type(type)
                    .listingId(listingId)
                    .build());
            conversationId = conversation.getId();
            participantRepository.save(ConversationParticipant.builder()
                    .conversationId(conversationId).userEmail(requesterEmail).unreadCount(0).build());
            participantRepository.save(ConversationParticipant.builder()
                    .conversationId(conversationId).userEmail(recipientEmail).unreadCount(0).build());
        }

        return toConversationResponse(conversationRepository.findById(conversationId).orElseThrow(), requesterEmail);
    }

    public List<ConversationResponse> listConversations(String userEmail) {
        return participantRepository.findByUserEmail(userEmail).stream()
                .map(participant -> conversationRepository.findById(participant.getConversationId()).orElseThrow())
                .map(conversation -> toConversationResponse(conversation, userEmail))
                .collect(Collectors.toList());
    }

    public List<MessageResponse> listMessages(Long conversationId, String requesterEmail) {
        requireParticipant(conversationId, requesterEmail);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public MessageResponse sendMessage(Long conversationId, SendMessageRequest request, String senderEmail) {
        requireParticipant(conversationId, senderEmail);

        for (ConversationParticipant participant : participantRepository.findByConversationId(conversationId)) {
            if (!participant.getUserEmail().equalsIgnoreCase(senderEmail)
                    && (blockedUserRepository.existsByBlockerEmailAndBlockedEmail(participant.getUserEmail(), senderEmail))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The recipient has blocked you");
            }
        }

        Message message = messageRepository.save(Message.builder()
                .conversationId(conversationId)
                .senderEmail(senderEmail)
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .build());

        for (ConversationParticipant participant : participantRepository.findByConversationId(conversationId)) {
            if (!participant.getUserEmail().equalsIgnoreCase(senderEmail)) {
                participant.setUnreadCount(participant.getUnreadCount() + 1);
                participantRepository.save(participant);
            }
        }

        return toMessageResponse(message);
    }

    @Transactional
    public void markRead(Long conversationId, String userEmail) {
        ConversationParticipant participant = requireParticipant(conversationId, userEmail);
        participant.setUnreadCount(0);
        participant.setLastReadAt(Instant.now());
        participantRepository.save(participant);
    }

    public void block(String blockerEmail, String blockedEmail) {
        if (blockedUserRepository.findByBlockerEmailAndBlockedEmail(blockerEmail, blockedEmail).isEmpty()) {
            blockedUserRepository.save(BlockedUser.builder().blockerEmail(blockerEmail).blockedEmail(blockedEmail).build());
        }
    }

    public void unblock(String blockerEmail, String blockedEmail) {
        blockedUserRepository.deleteByBlockerEmailAndBlockedEmail(blockerEmail, blockedEmail);
    }

    public List<String> listBlocked(String blockerEmail) {
        return blockedUserRepository.findByBlockerEmail(blockerEmail).stream()
                .map(BlockedUser::getBlockedEmail)
                .collect(Collectors.toList());
    }

    private Long findExistingConversation(Long listingId, String userA, String userB) {
        List<ConversationParticipant> aParticipations = participantRepository.findByUserEmail(userA);
        for (ConversationParticipant participation : aParticipations) {
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
                    .anyMatch(p -> p.getUserEmail().equalsIgnoreCase(userB));
            if (hasOtherUser) {
                return conversation.getId();
            }
        }
        return null;
    }

    private ConversationParticipant requireParticipant(Long conversationId, String userEmail) {
        return participantRepository.findByConversationIdAndUserEmail(conversationId, userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not part of this conversation"));
    }

    private ConversationResponse toConversationResponse(Conversation conversation, String requesterEmail) {
        List<ConversationParticipant> participants = participantRepository.findByConversationId(conversation.getId());
        int unread = participants.stream()
                .filter(p -> p.getUserEmail().equalsIgnoreCase(requesterEmail))
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
