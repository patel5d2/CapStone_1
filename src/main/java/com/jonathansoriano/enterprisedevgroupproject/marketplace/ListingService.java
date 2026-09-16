package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingRequest;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingResponse;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ReportRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    public List<ListingResponse> search(ListingCategory category, ListingType listingType, ListingStatus status,
                                         Long schoolId, String courseCode, String keyword, String requesterEmail) {
        List<Listing> listings = listingRepository.search(category, listingType, status, schoolId, courseCode, keyword);
        Set<Long> favoritedIds = favoritedListingIds(requesterEmail);
        return listings.stream().map(listing -> toResponse(listing, favoritedIds)).collect(Collectors.toList());
    }

    public ListingResponse get(Long id, String requesterEmail) {
        Listing listing = findOrThrow(id);
        return toResponse(listing, favoritedListingIds(requesterEmail));
    }

    public List<ListingResponse> myListings(String sellerEmail) {
        Set<Long> favoritedIds = favoritedListingIds(sellerEmail);
        return listingRepository.findBySellerEmailOrderByCreatedAtDesc(sellerEmail).stream()
                .map(listing -> toResponse(listing, favoritedIds))
                .collect(Collectors.toList());
    }

    public List<ListingResponse> myFavorites(String userEmail) {
        List<Long> favoritedIds = favoriteRepository.findByUserEmailOrderByCreatedAtDesc(userEmail).stream()
                .map(ListingFavorite::getListingId)
                .collect(Collectors.toList());
        Set<Long> favoritedSet = Set.copyOf(favoritedIds);
        return listingRepository.findAllById(favoritedIds).stream()
                .map(listing -> toResponse(listing, favoritedSet))
                .collect(Collectors.toList());
    }

    public ListingResponse create(ListingRequest request, String sellerEmail) {
        Listing listing = Listing.builder()
                .sellerEmail(sellerEmail)
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

    public ListingResponse update(Long id, ListingRequest request, String requesterEmail) {
        Listing listing = findOrThrow(id);
        requireOwner(listing, requesterEmail);

        listing.setTitle(request.getTitle());
        listing.setDescription(request.getDescription());
        listing.setCategory(request.getCategory());
        listing.setListingType(request.getListingType());
        listing.setPrice(request.getListingType() == ListingType.FREE || request.getListingType() == ListingType.LOOKING_FOR
                ? null : request.getPrice());
        listing.setCourseCode(request.getCourseCode());
        listing.setSchoolId(request.getSchoolId());
        listing.setPhotoUrls(request.getPhotoUrls() == null ? List.of() : request.getPhotoUrls());

        return toResponse(listingRepository.save(listing), favoritedListingIds(requesterEmail));
    }

    public void delete(Long id, String requesterEmail) {
        Listing listing = findOrThrow(id);
        requireOwner(listing, requesterEmail);
        listingRepository.delete(listing);
    }

    public ListingResponse markSold(Long id, String requesterEmail) {
        Listing listing = findOrThrow(id);
        requireOwner(listing, requesterEmail);
        listing.setStatus(ListingStatus.SOLD);
        return toResponse(listingRepository.save(listing), favoritedListingIds(requesterEmail));
    }

    public void favorite(Long id, String userEmail) {
        findOrThrow(id);
        if (favoriteRepository.findByListingIdAndUserEmail(id, userEmail).isEmpty()) {
            favoriteRepository.save(ListingFavorite.builder().listingId(id).userEmail(userEmail).build());
        }
    }

    public void unfavorite(Long id, String userEmail) {
        favoriteRepository.deleteByListingIdAndUserEmail(id, userEmail);
    }

    public void report(Long id, ReportRequest request, String reporterEmail) {
        findOrThrow(id);
        reportRepository.save(ListingReport.builder()
                .listingId(id)
                .reporterEmail(reporterEmail)
                .reason(request.getReason())
                .build());
    }

    private Listing findOrThrow(Long id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found: " + id));
    }

    private void requireOwner(Listing listing, String requesterEmail) {
        if (!listing.getSellerEmail().equalsIgnoreCase(requesterEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the seller can modify this listing");
        }
    }

    private Set<Long> favoritedListingIds(String userEmail) {
        if (userEmail == null) {
            return Set.of();
        }
        return favoriteRepository.findByUserEmailOrderByCreatedAtDesc(userEmail).stream()
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
                .photoUrls(listing.getPhotoUrls())
                .favorited(favoritedIds.contains(listing.getId()))
                .createdAt(listing.getCreatedAt())
                .updatedAt(listing.getUpdatedAt())
                .build();
    }
}
