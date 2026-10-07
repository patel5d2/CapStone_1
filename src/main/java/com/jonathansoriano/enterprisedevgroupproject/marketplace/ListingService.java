package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import com.jonathansoriano.enterprisedevgroupproject.identity.Party;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingRequest;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingResponse;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ReportRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Listings, favorites and reports, owned by the Clerk subject (ADR-012, marketplace slice).
 * Every caller is a {@link Party} from {@code CallerIdentity#caller}; ownership is decided
 * by {@link Party#owns} on the stored subject, never by address.
 */
@Service
public class ListingService {

    private final ListingRepository listingRepository;
    private final ListingFavoriteRepository favoriteRepository;
    private final ListingReportRepository reportRepository;

    public ListingService(ListingRepository listingRepository,
                           ListingFavoriteRepository favoriteRepository,
                           ListingReportRepository reportRepository) {
        this.listingRepository = listingRepository;
        this.favoriteRepository = favoriteRepository;
        this.reportRepository = reportRepository;
    }

    /**
     * Read inside a transaction so the mapping below can reach {@code photoUrls}.
     *
     * <p>{@code @ElementCollection} is LAZY by default and {@code open-in-view} is false,
     * so without this the Hibernate session closes when the repository call returns and
     * {@link #toResponse} throws {@code LazyInitializationException} — which the catch-all
     * serves as an opaque 500. It stayed hidden until the first listing existed: with an
     * empty table nothing ever touched the collection.
     *
     * @param requester the signed-in caller, or null for an anonymous read
     */
    @Transactional(readOnly = true)
    public List<ListingResponse> search(ListingCategory category, ListingType listingType, ListingStatus status,
                                         Long schoolId, String courseCode, String keyword, Party requester) {
        List<Listing> listings = listingRepository.search(category, listingType, status, schoolId, courseCode, keyword);
        Set<Long> favoritedIds = favoritedListingIds(requester);
        return listings.stream().map(listing -> toResponse(listing, favoritedIds)).collect(Collectors.toList());
    }

    /** Read inside a transaction for {@code photoUrls}; see {@link #search}. */
    @Transactional(readOnly = true)
    public ListingResponse get(Long id, Party requester) {
        Listing listing = findOrThrow(id);
        return toResponse(listing, favoritedListingIds(requester));
    }

    /** Read inside a transaction for {@code photoUrls}; see {@link #search}. */
    @Transactional(readOnly = true)
    public List<ListingResponse> myListings(Party seller) {
        Set<Long> favoritedIds = favoritedListingIds(seller);
        return listingRepository.findBySellerSubjectOrderByCreatedAtDesc(seller.subject()).stream()
                .map(listing -> toResponse(listing, favoritedIds))
                .collect(Collectors.toList());
    }

    /** Read inside a transaction for {@code photoUrls}; see {@link #search}. */
    @Transactional(readOnly = true)
    public List<ListingResponse> myFavorites(Party user) {
        List<Long> favoritedIds = favoriteRepository.findByUserSubjectOrderByCreatedAtDesc(user.subject()).stream()
                .map(ListingFavorite::getListingId)
                .collect(Collectors.toList());
        Set<Long> favoritedSet = Set.copyOf(favoritedIds);
        return listingRepository.findAllById(favoritedIds).stream()
                .map(listing -> toResponse(listing, favoritedSet))
                .collect(Collectors.toList());
    }

    public ListingResponse create(ListingRequest request, Party seller) {
        Listing listing = Listing.builder()
                .sellerEmail(seller.email())
                .sellerSubject(seller.subject())
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .listingType(request.getListingType())
                .status(ListingStatus.AVAILABLE)
                .price(request.getListingType() == ListingType.FREE || request.getListingType() == ListingType.LOOKING_FOR
                        ? null : request.getPrice())
                .courseCode(request.getCourseCode())
                .schoolId(request.getSchoolId())
                .photoUrls(request.getPhotoUrls() == null ? List.of() : request.getPhotoUrls())
                .build();

        return toResponse(listingRepository.save(listing), Set.of());
    }

    public ListingResponse update(Long id, ListingRequest request, Party requester) {
        Listing listing = findOrThrow(id);
        requireOwner(listing, requester);

        listing.setTitle(request.getTitle());
        listing.setDescription(request.getDescription());
        listing.setCategory(request.getCategory());
        listing.setListingType(request.getListingType());
        listing.setPrice(request.getListingType() == ListingType.FREE || request.getListingType() == ListingType.LOOKING_FOR
                ? null : request.getPrice());
        listing.setCourseCode(request.getCourseCode());
        listing.setSchoolId(request.getSchoolId());
        listing.setPhotoUrls(request.getPhotoUrls() == null ? List.of() : request.getPhotoUrls());

        return toResponse(listingRepository.save(listing), favoritedListingIds(requester));
    }

    public void delete(Long id, Party requester) {
        Listing listing = findOrThrow(id);
        requireOwner(listing, requester);
        listingRepository.delete(listing);
    }

    /**
     * Writes, then maps — so it needs the session open for the same reason the reads do.
     * Unlike {@link #update} it never replaces {@code photoUrls}, so the collection it
     * maps is still the lazy one loaded from the database.
     */
    @Transactional
    public ListingResponse markSold(Long id, Party requester) {
        Listing listing = findOrThrow(id);
        requireOwner(listing, requester);
        listing.setStatus(ListingStatus.SOLD);
        return toResponse(listingRepository.save(listing), favoritedListingIds(requester));
    }

    public void favorite(Long id, Party user) {
        findOrThrow(id);
        if (favoriteRepository.findByListingIdAndUserSubject(id, user.subject()).isPresent()) {
            return;
        }
        try {
            favoriteRepository.saveAndFlush(ListingFavorite.builder()
                    .listingId(id).userEmail(user.email()).userSubject(user.subject()).build());
        } catch (DataIntegrityViolationException collision) {
            // A favorite on this listing sits on the same address but belongs to the account
            // that held it before (a recycled address); uk_listing_favorite still keys on it.
            // ponytail: goes away when the email columns and their constraint do (S1-12).
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This listing is already saved under your address by a previous account.");
        }
    }

    @Transactional
    public void unfavorite(Long id, Party user) {
        favoriteRepository.deleteByListingIdAndUserSubject(id, user.subject());
    }

    public void report(Long id, ReportRequest request, Party reporter) {
        findOrThrow(id);
        reportRepository.save(ListingReport.builder()
                .listingId(id)
                .reporterEmail(reporter.email())
                .reporterSubject(reporter.subject())
                .reason(request.getReason())
                .build());
    }

    private Listing findOrThrow(Long id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found: " + id));
    }

    /** Invariant 2: called on a loaded row, so a missing listing is 404 and someone else's is 403. */
    private void requireOwner(Listing listing, Party requester) {
        if (!requester.owns(listing.getSellerSubject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the seller can modify this listing");
        }
    }

    private Set<Long> favoritedListingIds(Party user) {
        if (user == null) {
            return Set.of();
        }
        return favoriteRepository.findByUserSubjectOrderByCreatedAtDesc(user.subject()).stream()
                .map(ListingFavorite::getListingId)
                .collect(Collectors.toSet());
    }

    private ListingResponse toResponse(Listing listing, Set<Long> favoritedIds) {
        return ListingResponse.builder()
                .id(listing.getId())
                .sellerEmail(listing.getSellerEmail())
                .title(listing.getTitle())
                .description(listing.getDescription())
                .category(listing.getCategory())
                .listingType(listing.getListingType())
                .status(listing.getStatus())
                .price(listing.getPrice())
                .courseCode(listing.getCourseCode())
                .schoolId(listing.getSchoolId())
                // Copied, not handed over: the lazy collection itself would only be read
                // when Jackson writes the response, after the transaction has closed.
                .photoUrls(List.copyOf(listing.getPhotoUrls()))
                .favorited(favoritedIds.contains(listing.getId()))
                .createdAt(listing.getCreatedAt())
                .updatedAt(listing.getUpdatedAt())
                .build();
    }
}
