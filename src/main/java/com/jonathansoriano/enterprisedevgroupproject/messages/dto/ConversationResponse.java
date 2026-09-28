package com.jonathansoriano.enterprisedevgroupproject.messages.dto;

import com.jonathansoriano.enterprisedevgroupproject.messages.ConversationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConversationResponse {
    private Long id;
    private ConversationType type;
    private Long listingId;
    private List<String> participantEmails;
    private int unreadCount;
    private Instant createdAt;
}
