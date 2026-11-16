package com.jonathansoriano.enterprisedevgroupproject.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A listing as an administrator sees it: with its seller and how often it has been reported.
 *
 * @param photoUrl the first photo, enough to judge the listing by; null when it has none
 */
public record AdminListingResponse(Long id, String title, String description, String category, String listingType,
                                   String status, BigDecimal price, String sellerEmail, String schoolName,
                                   String photoUrl, long reportCount, Instant createdAt) {
}
