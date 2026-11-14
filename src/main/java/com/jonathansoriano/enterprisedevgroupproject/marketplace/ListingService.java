
package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingImageResponse;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingRequest;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingResponse;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ReportRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ListingService {

    private final ListingRepository listingRepository;
    private final ListingFavoriteRepository favoriteRepository;
    private final ListingReportRepository reportRepository;
    private final ListingImageRepository imageRepository;
    private final ListingImageCleanupService imageCleanupService;

    public ListingService(
            ListingRepository listingRepository,
            ListingFavoriteRepository favoriteRepository,
            ListingReportRepository reportRepository,
            ListingImageRepository imageRepository,
            ListingImageCleanupService imageCleanupService) {

        this.listingRepository = listingRepository;
        this.favoriteRepository = favoriteRepository;
        this.reportRepository = reportRepository;
        this.imageRepository = imageRepository;
        this.imageCleanupService = imageCleanupService;
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> search(
            ListingCategory category,
            ListingType listingType,
            ListingStatus status,
            Long schoolId,
            String courseCode,
            String keyword,
            String requesterEmail) {

        List<Listing> listings = listingRepository.search(
                category, listingType, status, schoolId, courseCode, keyword
        );

        Set<Long> favoritedIds = favoritedListingIds(requesterEmail);

        return listings.stream()
                .map(listing -> toResponse(listing, favoritedIds))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ListingResponse get(Long id, String requesterEmail) {
        Listing listing = findOrThrow(id);
        return toResponse(listing, favoritedListingIds(requesterEmail));
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> myListings(String sellerEmail) {
        Set<Long> favoritedIds = favoritedListingIds(sellerEmail);

        return listingRepository
                .findBySellerEmailOrderByCreatedAtDesc(sellerEmail)
                .stream()
                .map(listing -> toResponse(listing, favoritedIds))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> myFavorites(String userEmail) {
        List<Long> favoritedIds = favoriteRepository
                .findByUserEmailOrderByCreatedAtDesc(userEmail)
                .stream()
                .map(ListingFavorite::getListingId)
                .collect(Collectors.toList());

        Set<Long> favoritedSet = Set.copyOf(favoritedIds);

        return listingRepository.findAllById(favoritedIds)
                .stream()
                .map(listing -> toResponse(listing, favoritedSet))
                .collect(Collectors.toList());
    }

    @Transactional
    public ListingResponse create(ListingRequest request, String sellerEmail) {
        Listing listing = Listing.builder()
                .sellerEmail(sellerEmail)
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .listingType(request.getListingType())
                .status(ListingStatus.AVAILABLE)
                .price(request.getListingType() == ListingType.FREE
                        || request.getListingType() == ListingType.LOOKING_FOR
                        ? null : request.getPrice())
                .courseCode(request.getCourseCode())
                .schoolId(request.getSchoolId())
                .build();

        List<ListingImage> images =
                resolveImages(request.getPhotoIds(), sellerEmail, null);

        for (ListingImage image : images) {
            image.setListing(listing);
            listing.getPhotos().add(image);
        }

        Listing saved = listingRepository.save(listing);

        return toResponse(saved, Set.of());
    }

    @Transactional
    public ListingResponse update(
            Long id,
            ListingRequest request,
            String requesterEmail) {

        Listing listing = findOrThrow(id);
        requireOwner(listing, requesterEmail);

        List<ListingImage> newImages =
                resolveImages(request.getPhotoIds(), requesterEmail, id);

        Set<String> retainedIds = newImages.stream()
                .map(ListingImage::getId)
                .collect(Collectors.toSet());

        List<String> removedPublicIds = listing.getPhotos().stream()
                .filter(image -> !retainedIds.contains(image.getId()))
                .map(ListingImage::getPublicId)
                .filter(publicId -> publicId != null && !publicId.isBlank())
                .collect(Collectors.toList());

        listing.setTitle(request.getTitle());
        listing.setDescription(request.getDescription());
        listing.setCategory(request.getCategory());
        listing.setListingType(request.getListingType());
        listing.setPrice(request.getListingType() == ListingType.FREE
                || request.getListingType() == ListingType.LOOKING_FOR
                ? null : request.getPrice());
        listing.setCourseCode(request.getCourseCode());
        listing.setSchoolId(request.getSchoolId());

        listing.getPhotos().clear();

        for (ListingImage image : newImages) {
            image.setListing(listing);
            listing.getPhotos().add(image);
        }

        Listing saved = listingRepository.save(listing);

        imageCleanupService.deleteAfterCommit(removedPublicIds);

        return toResponse(saved, favoritedListingIds(requesterEmail));
    }

    @Transactional
    public void delete(Long id, String requesterEmail) {
        Listing listing = findOrThrow(id);
        requireOwner(listing, requesterEmail);

        List<String> publicIds = listing.getPhotos().stream()
                .map(ListingImage::getPublicId)
                .filter(publicId -> publicId != null && !publicId.isBlank())
                .collect(Collectors.toList());

        listingRepository.delete(listing);

        imageCleanupService.deleteAfterCommit(publicIds);
    }

    @Transactional
    public ListingResponse markSold(Long id, String requesterEmail) {
        Listing listing = findOrThrow(id);
        requireOwner(listing, requesterEmail);

        listing.setStatus(ListingStatus.SOLD);

        return toResponse(
                listingRepository.save(listing),
                favoritedListingIds(requesterEmail)
        );
    }

    public void favorite(Long id, String userEmail) {
        findOrThrow(id);

        if (favoriteRepository
                .findByListingIdAndUserEmail(id, userEmail)
                .isEmpty()) {

            favoriteRepository.save(
                    ListingFavorite.builder()
                            .listingId(id)
                            .userEmail(userEmail)
                            .build()
            );
        }
    }

    @Transactional
    public void unfavorite(Long id, String userEmail) {
        favoriteRepository.deleteByListingIdAndUserEmail(id, userEmail);
    }

    public void report(
            Long id,
            ReportRequest request,
            String reporterEmail) {

        findOrThrow(id);

        reportRepository.save(
                ListingReport.builder()
                        .listingId(id)
                        .reporterEmail(reporterEmail)
                        .reason(request.getReason())
                        .build()
        );
    }

    private List<ListingImage> resolveImages(
            List<String> requestedIds,
            String requesterEmail,
            Long currentListingId) {

        if (requestedIds == null
                || requestedIds.size() < ListingRequest.MIN_PHOTOS
                || requestedIds.size() > ListingRequest.MAX_PHOTOS) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A listing must have between 1 and 4 photos."
            );
        }

        if (new HashSet<>(requestedIds).size() != requestedIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Duplicate photo IDs are not allowed."
            );
        }

        List<ListingImage> found = imageRepository.findAllById(requestedIds);

        if (found.size() != requestedIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "One or more photos could not be found."
            );
        }

        Map<String, ListingImage> byId = found.stream()
                .collect(Collectors.toMap(
                        ListingImage::getId,
                        Function.identity()
                ));

        List<ListingImage> ordered = new ArrayList<>();

        for (String imageId : requestedIds) {
            ListingImage image = byId.get(imageId);

            if (image == null
                    || image.getOwnerEmail() == null
                    || !image.getOwnerEmail().equalsIgnoreCase(requesterEmail)) {

                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You cannot attach a photo uploaded by another user."
                );
            }

            Listing attachedListing = image.getListing();

            if (attachedListing != null
                    && (currentListingId == null
                    || !currentListingId.equals(attachedListing.getId()))) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A photo is already attached to another listing."
                );
            }

            ordered.add(image);
        }

        return ordered;
    }

    private Listing findOrThrow(Long id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Listing not found: " + id
                ));
    }

    private void requireOwner(Listing listing, String requesterEmail) {
        if (requesterEmail == null
                || !listing.getSellerEmail().equalsIgnoreCase(requesterEmail)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the seller can modify this listing"
            );
        }
    }

    private Set<Long> favoritedListingIds(String userEmail) {
        if (userEmail == null) {
            return Set.of();
        }

        return favoriteRepository
                .findByUserEmailOrderByCreatedAtDesc(userEmail)
                .stream()
                .map(ListingFavorite::getListingId)
                .collect(Collectors.toSet());
    }

    private ListingResponse toResponse(
            Listing listing,
            Set<Long> favoritedIds) {

        List<ListingImageResponse> photos = listing.getPhotos().stream()
                .map(image -> ListingImageResponse.builder()
                        .id(image.getId())
                        .photoUrl(image.getPhotoUrl())
                        .build())
                .collect(Collectors.toList());

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
                .photos(photos)
                .favorited(favoritedIds.contains(listing.getId()))
                .createdAt(listing.getCreatedAt())
                .updatedAt(listing.getUpdatedAt())
                .build();
    }
}
