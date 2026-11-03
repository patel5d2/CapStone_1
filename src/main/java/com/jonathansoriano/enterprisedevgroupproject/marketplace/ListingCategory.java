package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import java.util.EnumSet;
import java.util.Set;

/**
 * Each category lists the {@link ListingType}s that make sense for it: textbooks and
 * electronics can be rented, clothes, event tickets and furniture cannot. The frontend
 * mirrors this table in {@code ALLOWED_TYPES} (pages/Marketplace.tsx); keep them in step.
 */
public enum ListingCategory {
    BOOKS(EnumSet.allOf(ListingType.class)),
    CLOTHES(EnumSet.of(ListingType.SELL, ListingType.FREE, ListingType.LOOKING_FOR)),
    EVENT_TICKETS(EnumSet.of(ListingType.SELL, ListingType.FREE, ListingType.LOOKING_FOR)),
    FURNITURE(EnumSet.of(ListingType.SELL, ListingType.FREE, ListingType.LOOKING_FOR)),
    ELECTRONICS(EnumSet.allOf(ListingType.class)),
    OTHER(EnumSet.allOf(ListingType.class));

    private final Set<ListingType> allowedTypes;

    ListingCategory(Set<ListingType> allowedTypes) {
        this.allowedTypes = allowedTypes;
    }

    public boolean allows(ListingType type) {
        return allowedTypes.contains(type);
    }
}
