package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import com.jonathansoriano.enterprisedevgroupproject.identity.IdentityClaim;
import org.springframework.stereotype.Component;

/**
 * Binds marketplace rows (listings, favorites, reports) on a caller's verified address to
 * their subject. Once per subject is enough: these rows are only ever written by their
 * owner, who now always carries a subject.
 */
@Component
class MarketplaceClaims implements IdentityClaim {

    private final ListingRepository listings;
    private final ListingFavoriteRepository favorites;
    private final ListingReportRepository reports;

    MarketplaceClaims(ListingRepository listings, ListingFavoriteRepository favorites,
                      ListingReportRepository reports) {
        this.listings = listings;
        this.favorites = favorites;
        this.reports = reports;
    }

    @Override
    public void claim(String email, String subject) {
        listings.claim(email, subject);
        favorites.claim(email, subject);
        reports.claim(email, subject);
    }
}
