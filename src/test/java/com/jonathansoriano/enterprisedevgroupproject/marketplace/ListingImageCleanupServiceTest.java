package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import com.jonathansoriano.enterprisedevgroupproject.image.CloudinaryClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Arrays;

import static org.mockito.Mockito.*;

class ListingImageCleanupServiceTest {

    private final CloudinaryClient cloudinary = mock(CloudinaryClient.class);
    private final ListingImageCleanupService cleanupService =
            new ListingImageCleanupService(cloudinary);

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteAfterCommit_shouldWaitForCommitAndDeduplicatePublicIds() {
        TransactionSynchronizationManager.initSynchronization();

        cleanupService.deleteAfterCommit(Arrays.asList(
                "campusbridge/listings/one",
                "campusbridge/listings/one",
                null,
                " ",
                "campusbridge/listings/two"));

        verifyNoInteractions(cloudinary);
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(cloudinary).delete("campusbridge/listings/one");
        verify(cloudinary).delete("campusbridge/listings/two");
        verifyNoMoreInteractions(cloudinary);
    }

    @Test
    void deleteAfterCommit_shouldNotDeleteAssetsWhenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        cleanupService.deleteAfterCommit(List.of("campusbridge/listings/one"));

        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verifyNoInteractions(cloudinary);
    }

    @Test
    void deleteAfterCommit_shouldContinueAfterCloudinaryDeleteFailure() {
        TransactionSynchronizationManager.initSynchronization();
        doThrow(new IllegalStateException("provider unavailable"))
                .when(cloudinary).delete("campusbridge/listings/failed");
        cleanupService.deleteAfterCommit(List.of(
                "campusbridge/listings/failed",
                "campusbridge/listings/success"));

        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(cloudinary).delete("campusbridge/listings/failed");
        verify(cloudinary).delete("campusbridge/listings/success");
        verifyNoMoreInteractions(cloudinary);
    }

    @Test
    void deleteAfterCommit_shouldIgnoreBlankOrMissingPublicIds() {
        TransactionSynchronizationManager.initSynchronization();

        cleanupService.deleteAfterCommit(Arrays.asList(null, "", "   "));

        verifyNoInteractions(cloudinary);
        org.assertj.core.api.Assertions.assertThat(
                TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void deleteAfterCommit_shouldDeleteImmediatelyWhenNoTransactionIsActive() {
        cleanupService.deleteAfterCommit(List.of("campusbridge/listings/one"));

        verify(cloudinary).delete("campusbridge/listings/one");
    }
}
