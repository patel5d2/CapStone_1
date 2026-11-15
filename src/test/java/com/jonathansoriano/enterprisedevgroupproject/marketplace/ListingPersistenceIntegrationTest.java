package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import com.jonathansoriano.enterprisedevgroupproject.image.CloudinaryClient;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingRequest;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("prod")
class ListingPersistenceIntegrationTest {

    private static final String OWNER = "listing-owner@example.test";
    private static final String BUYER = "listing-buyer@example.test";

    @Autowired
    private ListingService listingService;
    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private ListingImageRepository imageRepository;
    @Autowired
    private ListingFavoriteRepository favoriteRepository;
    @Autowired
    private ListingReportRepository reportRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private CloudinaryClient cloudinary;

    private final List<Long> testListingIds = new ArrayList<>();
    private final List<String> testStagedImageIds = new ArrayList<>();

    @AfterEach
    void cleanUpTestData() {
        transaction().executeWithoutResult(status -> {
            for (Long listingId : testListingIds) {
                listingRepository.findById(listingId).ifPresent(listingRepository::delete);
            }
            for (String imageId : testStagedImageIds) {
                imageRepository.deleteById(imageId);
            }
        });
        testListingIds.clear();
        testStagedImageIds.clear();
    }

    @Test
    void updateListing_shouldPersistRetainedAndNewPhotosAndDeleteOrphans() throws InterruptedException {
        Fixture fixture = createListingWithImages(
                OWNER,
                "Original title",
                List.of("existing-remove", "existing-keep"));
        ListingImage stagedImage = createStagedImage(OWNER, "new-image");
        var oldTimestamp = listingService.get(fixture.listingId(), OWNER).getUpdatedAt();
        Thread.sleep(25);

        ListingResponse updated = listingService.update(
                fixture.listingId(),
                request("Updated title", "Updated description",
                        List.of(fixture.imageIds().get(1), stagedImage.getId())),
                OWNER);

        assertThat(updated.getTitle()).isEqualTo("Updated title");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
        assertThat(updated.getCategory()).isEqualTo(ListingCategory.ELECTRONICS);
        assertThat(updated.getListingType()).isEqualTo(ListingType.RENT);
        assertThat(updated.getPrice()).isEqualByComparingTo("22.50");
        assertThat(updated.getPhotos())
                .extracting(photo -> photo.getId())
                .containsExactly(fixture.imageIds().get(1), stagedImage.getId());
        assertThat(updated.getPhotos())
                .extracting(photo -> photo.getPhotoUrl())
                .containsExactly(
                        imageRepository.findById(fixture.imageIds().get(1))
                                .orElseThrow().getPhotoUrl(),
                        stagedImage.getPhotoUrl());
        assertThat(updated.getUpdatedAt()).isAfter(oldTimestamp);

        assertThat(imageRepository.findById(fixture.imageIds().get(0))).isEmpty();
        assertThat(imageListingId(fixture.imageIds().get(1))).isEqualTo(fixture.listingId());
        assertThat(imageListingId(stagedImage.getId())).isEqualTo(fixture.listingId());
        verify(cloudinary).delete("existing-remove");
        verifyNoMoreInteractions(cloudinary);
    }

    @Test
    void updateListing_shouldLeaveDatabaseUnchangedWhenPhotoIdsAreInvalid() {
        Fixture fixture = createListingWithImages(
                OWNER, "Original title", List.of("still-attached"));
        long imageCountBefore = countImages(fixture.listingId());

        assertThatThrownBy(() -> listingService.update(
                fixture.listingId(),
                request("Should not be saved", "Invalid photo", List.of(UUID.randomUUID().toString())),
                OWNER))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(listingService.get(fixture.listingId(), OWNER).getTitle())
                .isEqualTo("Original title");
        assertThat(countImages(fixture.listingId())).isEqualTo(imageCountBefore);
        verifyNoInteractions(cloudinary);
    }

    @Test
    void deleteListing_shouldCascadeToImagesFavoritesAndReportsWithoutAffectingOtherListings() {
        Fixture deletedFixture = createListingWithImages(
                OWNER, "Delete target", Arrays.asList("duplicate-public-id", "duplicate-public-id", null));
        Fixture unrelatedFixture = createListingWithImages(
                BUYER, "Keep target", List.of("unrelated-public-id"));
        transaction().executeWithoutResult(status -> {
            favoriteRepository.save(ListingFavorite.builder()
                    .listingId(deletedFixture.listingId())
                    .userEmail(BUYER)
                    .build());
            reportRepository.save(ListingReport.builder()
                    .listingId(deletedFixture.listingId())
                    .reporterEmail(BUYER)
                    .reason("test report")
                    .build());
        });

        listingService.delete(deletedFixture.listingId(), OWNER);

        assertThat(listingRepository.findById(deletedFixture.listingId())).isEmpty();
        assertThat(count("listing_image", deletedFixture.listingId())).isZero();
        assertThat(count("listing_favorite", deletedFixture.listingId())).isZero();
        assertThat(count("listing_report", deletedFixture.listingId())).isZero();
        assertThat(listingRepository.findById(unrelatedFixture.listingId())).isPresent();
        assertThat(count("listing_image", unrelatedFixture.listingId())).isEqualTo(1);
        verify(cloudinary).delete("duplicate-public-id");
        verifyNoMoreInteractions(cloudinary);
    }

    @Test
    void deleteListing_shouldNotCleanCloudinaryWhenTransactionRollsBack() {
        Fixture fixture = createListingWithImages(
                OWNER, "Rollback target", List.of("rollback-public-id"));

        transaction().executeWithoutResult(status -> {
            listingService.delete(fixture.listingId(), OWNER);
            verifyNoInteractions(cloudinary);
            status.setRollbackOnly();
        });

        assertThat(listingRepository.findById(fixture.listingId())).isPresent();
        assertThat(count("listing_image", fixture.listingId())).isEqualTo(1);
        verifyNoInteractions(cloudinary);
    }

    @Test
    void deleteListing_shouldKeepDatabaseDeletionWhenCloudinaryCleanupFails() {
        Fixture fixture = createListingWithImages(
                OWNER, "Provider failure target", List.of("unavailable-public-id"));
        doThrow(new IllegalStateException("Cloudinary unavailable"))
                .when(cloudinary).delete("unavailable-public-id");

        listingService.delete(fixture.listingId(), OWNER);

        assertThat(listingRepository.findById(fixture.listingId())).isEmpty();
        assertThat(count("listing_image", fixture.listingId())).isZero();
        verify(cloudinary).delete("unavailable-public-id");
    }

    @Test
    void deleteListing_shouldRejectNonOwnerAndUnknownListingWithoutSideEffects() {
        Fixture fixture = createListingWithImages(
                OWNER, "Protected target", List.of("protected-public-id"));

        assertThatThrownBy(() -> listingService.delete(fixture.listingId(), BUYER))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> listingService.delete(-1L, OWNER))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(listingRepository.findById(fixture.listingId())).isPresent();
        assertThat(count("listing_image", fixture.listingId())).isEqualTo(1);
        verifyNoInteractions(cloudinary);
    }

    private Fixture createListingWithImages(
            String owner,
            String title,
            List<String> publicIds) {
        return transaction().execute(status -> {
            Listing listing = listing(owner, title);
            List<String> imageIds = new ArrayList<>();
            for (int i = 0; i < publicIds.size(); i++) {
                ListingImage image = new ListingImage();
                image.setOwnerEmail(owner);
                image.setPhotoUrl(photoUrl("seed-" + UUID.randomUUID()));
                image.setPublicId(publicIds.get(i));
                image.setListing(listing);
                listing.getPhotos().add(image);
                imageIds.add(image.getId());
            }
            Listing saved = listingRepository.saveAndFlush(listing);
            testListingIds.add(saved.getId());
            return new Fixture(saved.getId(), List.copyOf(imageIds));
        });
    }

    private ListingImage createStagedImage(String owner, String publicId) {
        return transaction().execute(status -> {
            ListingImage image = new ListingImage();
            image.setOwnerEmail(owner);
            image.setPhotoUrl(photoUrl("staged-" + UUID.randomUUID()));
            image.setPublicId(publicId);
            ListingImage staged = imageRepository.saveAndFlush(image);
            testStagedImageIds.add(staged.getId());
            return staged;
        });
    }

    private long countImages(Long listingId) {
        return count("listing_image", listingId);
    }

    private long imageListingId(String imageId) {
        return jdbc.queryForObject(
                "SELECT listing_id FROM listing_image WHERE id = ?",
                Long.class,
                imageId);
    }

    private long count(String table, Long listingId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE listing_id = ?",
                Long.class,
                listingId);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private static Listing listing(String owner, String title) {
        return Listing.builder()
                .sellerEmail(owner)
                .title(title)
                .description("Starting description")
                .category(ListingCategory.BOOKS)
                .listingType(ListingType.SELL)
                .price(new BigDecimal("10.00"))
                .build();
    }

    private static ListingRequest request(
            String title,
            String description,
            List<String> photoIds) {
        return ListingRequest.builder()
                .title(title)
                .description(description)
                .category(ListingCategory.ELECTRONICS)
                .listingType(ListingType.RENT)
                .price(new BigDecimal("22.50"))
                .photoIds(photoIds)
                .build();
    }

    private static String photoUrl(String id) {
        return "https://images.example.test/" + id + ".webp";
    }

    private record Fixture(Long listingId, List<String> imageIds) {
    }
}
