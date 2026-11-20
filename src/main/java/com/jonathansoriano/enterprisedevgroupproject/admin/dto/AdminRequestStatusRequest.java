package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

import com.jonathansoriano.enterprisedevgroupproject.support.RequestStatus;
import jakarta.validation.constraints.NotNull;

public record AdminRequestStatusRequest(@NotNull(message = "Status is required") RequestStatus status) {
}
