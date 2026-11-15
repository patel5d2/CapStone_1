package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingRequest;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    private static final String OWNER = "owner@example.test";
    private static final String OTHER_USER = "other@example.test";

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private ListingFavoriteRepository favoriteRepository;
    @Mock
    private ListingReportRepository reportRepository;
    @Mock
    private ListingImageRepository imageRepository;
    @Mock
    private ListingImageCleanupService imageCleanupService;

    @InjectMocks
    private ListingService listingService;

    private Listing existingListing;
    private ListingImage removedImage;
    private ListingImage retainedImage;
    private ListingImage addedImage;

    @BeforeEach
    void setUp() {
        existingListing = listing(17L, OWNER, "Original title");
        removedImage = image("removed-id", OWNER, existingListing, "removed-public-id");
        retainedImage = image("retained-id", OWNER, existingListing, "retained-public-id");
        existingListing.getPhotos().addAll(List.of(removedImage, retainedImage));
        addedImage = image("added-id", OWNER, null, "added-public-id");

        lenient().when(listingRepository.findById(17L)).thenReturn(Optional.of(existingListing));
        lenient().when(listingRepository.save(any(Listing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(favoriteRepository.findByUserEmailOrderByCreatedAtDesc(OWNER))
                .thenReturn(List.of());
    }

    @Test
    void updateListing_shouldUpdateFieldsAndPreserveRetainedAndNewImages() {
        when(imageRepository.findAllById(List.of("retained-id", "added-id")))
                .thenReturn(List.of(addedImage, retainedImage));
        ListingRequest request = updateRequest(
                "Updated title", "Updated description", ListingCategory.ELECTRONICS,
                ListingType.RENT, "20.00", List.of("retained-id", "added-id"));

        ListingResponse response = listingService.update(17L, request, OWNER);

        assertThat(existingListing.getTitle()).isEqualTo("Updated title");
        assertThat(existingListing.getDescription()).isEqualTo("Updated description");
        assertThat(existingListing.getCategory()).isEqualTo(ListingCategory.ELECTRONICS);
        assertThat(existingListing.getListingType()).isEqualTo(ListingType.RENT);
        assertThat(existingListing.getPrice()).isEqualByComparingTo("20.00");
        assertThat(existingListing.getPhotos()).containsExactly(retainedImage, addedImage);
        assertThat(retainedImage.getListing()).isSameAs(existingListing);
        assertThat(addedImage.getListing()).isSameAs(existingListing);
        assertThat(response.getPhotos())
                .extracting(photo -> photo.getId())
                .containsExactly("retained-id", "added-id");
        assertThat(response.getPhotos())
                .extracting(photo -> photo.getPhotoUrl())
                .containsExactly(retainedImage.getPhotoUrl(), addedImage.getPhotoUrl());
        verify(imageCleanupService).deleteAfterCommit(List.of("removed-public-id"));
    }

    @Test
    void updateListing_shouldRemoveRequestedImageAndRetainTheOther() {
        when(imageRepository.findAllById(List.of("retained-id")))
                .thenReturn(List.of(retainedImage));

        ListingResponse response = listingService.update(
                17L,
                updateRequest("Kept title", "Description", ListingCategory.BOOKS,
                        ListingType.SELL, "10.00", List.of("retained-id")),
                OWNER);

        assertThat(existingListing.getPhotos()).containsExactly(retainedImage);
        assertThat(response.getPhotos()).hasSize(1);
        assertThat(response.getPhotos().get(0).getId()).isEqualTo("retained-id");
        assertThat(response.getPhotos().get(0).getPhotoUrl()).isEqualTo(retainedImage.getPhotoUrl());
        verify(imageCleanupService).deleteAfterCommit(List.of("removed-public-id"));
    }

    @Test
    void updateListing_shouldRejectAnotherUsersListing() {
        assertThatThrownBy(() -> listingService.update(
                17L, updateRequest(List.of("retained-id")), OTHER_USER))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verifyNoInteractions(imageRepository, imageCleanupService);
        verify(listingRepository, never()).save(any());
    }

    @Test
    void updateListing_shouldReturnNotFoundForUnknownId() {
        when(listingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.update(
                999L, updateRequest(List.of("retained-id")), OWNER))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(imageRepository, imageCleanupService);
    }

    @Test
    void updateListing_shouldRejectAnImageOwnedByAnotherUserWithoutMutatingListing() {
        ListingImage foreignImage = image("foreign-id", OTHER_USER, null, "foreign-public-id");
        when(imageRepository.findAllById(List.of("foreign-id"))).thenReturn(List.of(foreignImage));

        assertThatThrownBy(() -> listingService.update(
                17L, updateRequest(List.of("foreign-id")), OWNER))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertListingUnchanged();
        verifyNoInteractions(imageCleanupService);
        verify(listingRepository, never()).save(any());
    }

    @Test
    void updateListing_shouldRejectAnImageAttachedToAnotherListing() {
        Listing anotherListing = listing(88L, OWNER, "Other listing");
        ListingImage attachedImage = image("attached-id", OWNER, anotherListing, "attached-public-id");
        when(imageRepository.findAllById(List.of("attached-id"))).thenReturn(List.of(attachedImage));

        assertThatThrownBy(() -> listingService.update(
                17L, updateRequest(List.of("attached-id")), OWNER))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertListingUnchanged();
        verifyNoInteractions(imageCleanupService);
        verify(listingRepository, never()).save(any());
    }

    @Test
    void updateListing_shouldRejectDuplicateImageIds() {
        assertThatThrownBy(() -> listingService.update(
                17L, updateRequest(List.of("retained-id", "retained-id")), OWNER))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertListingUnchanged();
        verifyNoInteractions(imageRepository, imageCleanupService);
    }

    @Test
    void updateListing_shouldRejectImageCountsOutsideOneToFour() {
        for (List<String> photoIds : List.<List<String>>of(
                List.of(),
                List.of("1", "2", "3", "4", "5"))) {
            assertThatThrownBy(() -> listingService.update(
                    17L, updateRequest(photoIds), OWNER))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        assertListingUnchanged();
        verifyNoInteractions(imageRepository, imageCleanupService);
    }

    @Test
    void updateListing_shouldRejectMissingPhotoIds() {
        ListingRequest request = updateRequest(List.of("retained-id"));
        request.setPhotoIds(null);

        assertThatThrownBy(() -> listingService.update(17L, request, OWNER))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertListingUnchanged();
        verifyNoInteractions(imageRepository, imageCleanupService);
    }

    @Test
    void createListing_shouldRejectMissingMinimumPhotoBeforeSaving() {
        ListingRequest request = updateRequest(List.of());

        assertThatThrownBy(() -> listingService.create(request, OWNER))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(listingRepository);
        verifyNoInteractions(imageRepository);
    }

    @Test
    void updateListing_shouldRejectInvalidPhotoIdsWithoutChangingExistingImages() {
        when(imageRepository.findAllById(List.of("retained-id", "missing-id")))
                .thenReturn(List.of(retainedImage));

        assertThatThrownBy(() -> listingService.update(
                17L, updateRequest("Must not be applied", List.of("retained-id", "missing-id")), OWNER))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertListingUnchanged();
        verifyNoInteractions(imageCleanupService);
        verify(listingRepository, never()).save(any());
    }

    @Test
    void deleteListing_shouldDeleteOwnedListingAndScheduleItsImageCleanup() {
        listingService.delete(17L, OWNER);

        verify(listingRepository).delete(existingListing);
        verify(imageCleanupService).deleteAfterCommit(
                List.of("removed-public-id", "retained-public-id"));
    }

    @Test
    void deleteListing_shouldRejectAnotherUsersListing() {
        assertThatThrownBy(() -> listingService.delete(17L, OTHER_USER))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(listingRepository, never()).delete(any(Listing.class));
        verifyNoInteractions(imageCleanupService);
    }

    @Test
    void deleteListing_shouldReturnNotFoundForUnknownId() {
        when(listingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.delete(999L, OWNER))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(listingRepository, never()).delete(any(Listing.class));
        verifyNoInteractions(imageCleanupService);
    }

    @Test
    void deleteListing_shouldIgnoreImagesWithoutACloudinaryPublicId() {
        ListingImage imageWithoutPublicId = image("no-public-id", OWNER, existingListing, null);
        existingListing.setPhotos(new ArrayList<>(List.of(imageWithoutPublicId)));

        listingService.delete(17L, OWNER);

        verify(listingRepository).delete(existingListing);
        verify(imageCleanupService).deleteAfterCommit(List.of());
    }

    private void assertListingUnchanged() {
        assertThat(existingListing.getTitle()).isEqualTo("Original title");
        assertThat(existingListing.getPhotos()).containsExactly(removedImage, retainedImage);
        assertThat(removedImage.getListing()).isSameAs(existingListing);
        assertThat(retainedImage.getListing()).isSameAs(existingListing);
    }

    private static Listing listing(Long id, String owner, String title) {
        Listing listing = Listing.builder()
                .id(id)
                .sellerEmail(owner)
                .title(title)
                .description("Original description")
                .category(ListingCategory.BOOKS)
                .listingType(ListingType.SELL)
                .price(new BigDecimal("15.00"))
                .build();
        listing.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        listing.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return listing;
    }

    private static ListingImage image(
            String id,
            String owner,
            Listing listing,
            String publicId) {
        ListingImage image = new ListingImage();
        image.setId(id);
        image.setOwnerEmail(owner);
        image.setPhotoUrl("https://images.example.test/" + id + ".webp");
        image.setPublicId(publicId);
        image.setListing(listing);
        return image;
    }

    private static ListingRequest updateRequest(List<String> photoIds) {
        return updateRequest("Updated title", "Updated description", ListingCategory.ELECTRONICS,
                ListingType.RENT, "20.00", photoIds);
    }

    private static ListingRequest updateRequest(String title, List<String> photoIds) {
        return updateRequest(title, "Updated description", ListingCategory.ELECTRONICS,
                ListingType.RENT, "20.00", photoIds);
    }

    private static ListingRequest updateRequest(
            String title,
            String description,
            ListingCategory category,
            ListingType listingType,
            String price,
            List<String> photoIds) {
        return ListingRequest.builder()
                .title(title)
                .description(description)
                .category(category)
                .listingType(listingType)
                .price(new BigDecimal(price))
                .photoIds(photoIds)
                .build();
    }
}
