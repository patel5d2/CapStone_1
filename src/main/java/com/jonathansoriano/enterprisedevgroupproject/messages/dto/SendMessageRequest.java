package com.jonathansoriano.enterprisedevgroupproject.messages.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SendMessageRequest {
    @NotBlank(message = "Message content is required")
    private String content;
    private String imageUrl;
}
