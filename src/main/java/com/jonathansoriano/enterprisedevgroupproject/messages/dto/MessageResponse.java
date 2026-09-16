package com.jonathansoriano.enterprisedevgroupproject.messages.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MessageResponse {
    private Long id;
    private Long conversationId;
    private String senderEmail;
    private String content;
    private String imageUrl;
    private Instant createdAt;
}
