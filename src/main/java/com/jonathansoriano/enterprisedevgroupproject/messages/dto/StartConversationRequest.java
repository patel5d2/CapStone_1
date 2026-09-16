package com.jonathansoriano.enterprisedevgroupproject.messages.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Either {@code listingId} (starts/reuses a marketplace chat with that listing's
 * seller) or {@code recipientEmail} (a direct chat) must be set.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StartConversationRequest {
    private Long listingId;
    private String recipientEmail;
}
