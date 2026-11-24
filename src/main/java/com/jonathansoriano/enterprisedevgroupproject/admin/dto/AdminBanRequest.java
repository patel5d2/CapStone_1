package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

/**
 * Options for banning an account.
 *
 * @param removeContent also remove what they posted; see {@code AccountContentRemoval}
 */
public record AdminBanRequest(boolean removeContent) {
}
