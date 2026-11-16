package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

import jakarta.validation.constraints.NotBlank;

/** The banned address to restore. In the body, never the URL, so it stays out of access logs. */
public record AdminLiftBanRequest(@NotBlank(message = "Email is required") String email) {
}
