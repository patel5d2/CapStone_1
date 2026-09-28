package com.jonathansoriano.enterprisedevgroupproject.marketplace;

/**
 * SELL/RENT carry a price; FREE and LOOKING_FOR do not (LOOKING_FOR is a
 * request for an item rather than an offer of one).
 */
public enum ListingType {
    SELL,
    RENT,
    FREE,
    LOOKING_FOR
}
