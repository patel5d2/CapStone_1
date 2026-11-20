package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

/**
 * One banned email address.
 *
 * @param studentId the profile still on this address, or null when the account was deleted
 * @param name      that profile's name, or null
 */
public record AdminBanResponse(String email, Long studentId, String name) {
}
