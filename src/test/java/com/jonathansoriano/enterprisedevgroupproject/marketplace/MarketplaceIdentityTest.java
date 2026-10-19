package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import com.jonathansoriano.enterprisedevgroupproject.identity.CallerIdentity;
import com.jonathansoriano.enterprisedevgroupproject.identity.Party;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingRequest;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingResponse;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ReportRequest;
import com.jonathansoriano.enterprisedevgroupproject.messages.ConversationParticipantRepository;
import com.jonathansoriano.enterprisedevgroupproject.messages.MessagingService;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.ConversationResponse;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.StartConversationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-012, marketplace slice: sellers, favoriters and reporters are keyed on the Clerk
 * subject, against the real schema and the real resolution path ({@link CallerIdentity#caller}).
 */
@SpringBootTest
@Transactional
class MarketplaceIdentityTest {

    @Autowired private CallerIdentity identity;
    @Autowired private ListingService listings;
    @Autowired private ListingRepository listingRepository;
    @Autowired private ListingFavoriteRepository favorites;
    @Autowired private ListingReportRepository reports;
    @Autowired private MessagingService messaging;
    @Autowired private ConversationParticipantRepository participants;

    private static Jwt token(String subject, String email) {
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .subject(subject).claim("email", email)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    private static ListingRequest request(String title) {
        return ListingRequest.builder().title(title).category(ListingCategory.BOOKS)
                .listingType(ListingType.SELL).price(new BigDecimal("10.00"))
                // Mutable: update() on a listing still managed by this test's transaction
                // merges the list in place, which an immutable List.of() refuses.
                .photoUrls(new ArrayList<>()).build();
    }

    private Listing legacyListing(String sellerEmail) {
        return listingRepository.saveAndFlush(Listing.builder().sellerEmail(sellerEmail).title("Legacy lamp")
                .category(ListingCategory.FURNITURE).listingType(ListingType.FREE).build());
    }

    @Test
    @DisplayName("an address change keeps the seller's listings and the buyer's favorites")
    void addressChangeKeepsOwnership() {
        Party sellerOld = identity.caller(token("user_seller_ac", "seller.ac@old.edu"));
        Party buyerOld = identity.caller(token("user_buyer_ac", "buyer.ac@old.edu"));
        ListingResponse listing = listings.create(request("Desk"), sellerOld);
        listings.favorite(listing.getId(), buyerOld);

        Party sellerNew = identity.caller(token("user_seller_ac", "seller.ac@new.edu"));
        Party buyerNew = identity.caller(token("user_buyer_ac", "buyer.ac@new.edu"));

        assertThat(listings.myListings(sellerNew)).extracting(ListingResponse::getId).containsExactly(listing.getId());
        assertThat(listings.update(listing.getId(), request("Desk, cheaper"), sellerNew).getTitle()).isEqualTo("Desk, cheaper");
        assertThat(listings.markSold(listing.getId(), sellerNew).getStatus()).isEqualTo(ListingStatus.SOLD);

        assertThat(listings.get(listing.getId(), buyerNew).isFavorited()).isTrue();
        assertThat(listings.myFavorites(buyerNew)).extracting(ListingResponse::getId).containsExactly(listing.getId());
        listings.unfavorite(listing.getId(), buyerNew);
        assertThat(listings.myFavorites(buyerNew)).isEmpty();

        listings.delete(listing.getId(), sellerNew);
        assertThat(listingRepository.findById(listing.getId())).isEmpty();
    }

    @Test
    @DisplayName("another subject gets 403 on owner-only operations; a missing listing gets 404")
    void ownerOnlyOperationsDistinguish403From404() {
        Party seller = identity.caller(token("user_seller_ow", "seller.ow@school.edu"));
        Party other = identity.caller(token("user_other_ow", "other.ow@school.edu"));
        Long id = listings.create(request("Chair"), seller).getId();

        assertThatThrownBy(() -> listings.update(id, request("mine now"), other)).hasMessageContaining("403");
        assertThatThrownBy(() -> listings.markSold(id, other)).hasMessageContaining("403");
        assertThatThrownBy(() -> listings.delete(id, other)).hasMessageContaining("403");

        assertThatThrownBy(() -> listings.update(Long.MAX_VALUE, request("x"), seller)).hasMessageContaining("404");
        assertThatThrownBy(() -> listings.markSold(Long.MAX_VALUE, seller)).hasMessageContaining("404");
        assertThatThrownBy(() -> listings.delete(Long.MAX_VALUE, seller)).hasMessageContaining("404");
    }

    @Test
    @DisplayName("a second account on a recycled address does not inherit listings or favorites")
    void recycledAddressDoesNotInheritOwnership() {
        Party original = identity.caller(token("user_orig_rc", "shared.rc@school.edu"));
        ListingResponse listing = listings.create(request("Bike"), original);
        listings.favorite(listing.getId(), original);

        Party newcomer = identity.caller(token("user_new_rc", "shared.rc@school.edu"));
        assertThat(listings.myListings(newcomer)).isEmpty();
        assertThat(listings.myFavorites(newcomer)).isEmpty();
        assertThat(listings.get(listing.getId(), newcomer).isFavorited()).isFalse();
        assertThatThrownBy(() -> listings.delete(listing.getId(), newcomer)).hasMessageContaining("403");
    }

    @Test
    @DisplayName("address-only legacy rows are claimed by the caller, case-insensitively, and then owned")
    void legacyRowsAreClaimedThenOwned() {
        Listing legacy = legacyListing("Legacy.LG@School.edu");
        favorites.saveAndFlush(ListingFavorite.builder().listingId(legacy.getId()).userEmail("legacy.lg@school.edu").build());
        reports.saveAndFlush(ListingReport.builder().listingId(legacy.getId()).reporterEmail("legacy.lg@school.edu")
                .reason("dupe").build());

        Party owner = identity.caller(token("user_legacy_lg", "legacy.lg@school.edu"));

        assertThat(listings.myListings(owner)).extracting(ListingResponse::getId).containsExactly(legacy.getId());
        assertThat(listings.myFavorites(owner)).extracting(ListingResponse::getId).containsExactly(legacy.getId());
        assertThat(reports.findAll()).filteredOn(r -> r.getListingId().equals(legacy.getId()))
                .singleElement().satisfies(r -> assertThat(r.getReporterSubject()).isEqualTo("user_legacy_lg"));
        assertThat(listings.markSold(legacy.getId(), owner).getStatus()).isEqualTo(ListingStatus.SOLD);
    }

    @Test
    @DisplayName("claiming skips a legacy favorite on a listing the subject already favorited")
    void claimDoesNotTripTheFavoriteSubjectIndex() {
        Listing listing = legacyListing("someone.dd@school.edu");
        favorites.saveAndFlush(ListingFavorite.builder().listingId(listing.getId())
                .userEmail("x.dd@new.edu").userSubject("user_x_dd").build());
        favorites.saveAndFlush(ListingFavorite.builder().listingId(listing.getId()).userEmail("x.dd@old.edu").build());

        Party x = identity.caller(token("user_x_dd", "x.dd@old.edu"));
        assertThat(favorites.findAll()).filteredOn(f -> "user_x_dd".equals(f.getUserSubject())).hasSize(1);
        assertThat(listings.myFavorites(x)).extracting(ListingResponse::getId).containsExactly(listing.getId());
    }

    @Test
    @DisplayName("a conversation from a listing reaches the seller recorded on it, not the address's new holder")
    void conversationFromListingUsesTheListingsSeller() {
        Party seller = identity.caller(token("user_seller_cv", "seller.cv@school.edu"));
        Long listingId = listings.create(request("Lamp"), seller).getId();
        Party buyer = identity.caller(token("user_buyer_cv", "buyer.cv@school.edu"));

        ConversationResponse chat = messaging.startConversation(
                StartConversationRequest.builder().listingId(listingId).build(), buyer);
        assertThat(participants.findByConversationId(chat.getId()))
                .extracting(p -> p.getUserSubject())
                .containsExactlyInAnyOrder("user_seller_cv", "user_buyer_cv");
    }

    @Test
    @DisplayName("reports record the reporter's subject")
    void reportsCarryTheSubject() {
        Party seller = identity.caller(token("user_seller_rp", "seller.rp@school.edu"));
        Long id = listings.create(request("Phone"), seller).getId();
        Party reporter = identity.caller(token("user_reporter_rp", "reporter.rp@school.edu"));
        listings.report(id, new ReportRequest("scam"), reporter);

        assertThat(reports.findAll()).filteredOn(r -> r.getListingId().equals(id))
                .singleElement().satisfies(r -> assertThat(r.getReporterSubject()).isEqualTo("user_reporter_rp"));
    }

    @Test
    @DisplayName("(listing, subject) stays unique under the favorite subject column")
    void favoriteSubjectIsUniquePerListing() {
        Listing listing = legacyListing("seller.uq@school.edu");
        favorites.saveAndFlush(ListingFavorite.builder().listingId(listing.getId())
                .userEmail("a.uq@old.edu").userSubject("user_a_uq").build());
        assertThatThrownBy(() -> favorites.saveAndFlush(ListingFavorite.builder().listingId(listing.getId())
                .userEmail("a.uq@new.edu").userSubject("user_a_uq").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
