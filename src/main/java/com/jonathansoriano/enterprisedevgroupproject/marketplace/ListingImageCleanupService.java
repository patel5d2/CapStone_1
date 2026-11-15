package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import com.jonathansoriano.enterprisedevgroupproject.image.CloudinaryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class ListingImageCleanupService {

    private static final Logger log =
            LoggerFactory.getLogger(ListingImageCleanupService.class);

    private final CloudinaryClient cloudinary;

    public ListingImageCleanupService(CloudinaryClient cloudinary) {
        this.cloudinary = cloudinary;
    }

    public void deleteAfterCommit(List<String> publicIds) {
        List<String> ids = publicIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return;
        }

        Runnable cleanup = () -> {
            for (String publicId : ids) {
                try {
                    cloudinary.delete(publicId);
                } catch (Exception ex) {
                    log.error(
                            "Could not delete Cloudinary listing image: {}",
                            publicId,
                            ex
                    );
                }
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            cleanup.run();
                        }
                    }
            );
        } else {
            cleanup.run();
        }
    }
}

