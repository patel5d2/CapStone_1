package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

/**
 * What the signed-in caller may do, for the SPA to decide what to show. Describes only the
 * caller themselves, so any signed-in account may ask.
 *
 * @param admin             the caller holds the administrator role
 * @param hasStudentProfile the caller also has a student profile; a dedicated administrator
 *                          account does not, and is shown account management only
 */
public record AdminAccessResponse(boolean admin, boolean hasStudentProfile) {
}
