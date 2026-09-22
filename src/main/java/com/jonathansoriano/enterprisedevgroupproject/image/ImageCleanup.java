package com.jonathansoriano.enterprisedevgroupproject.image;

import java.time.Instant;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableScheduling
@Slf4j
public class ImageCleanup {
    private final ImageAssetRepository assets;
    private final CloudinaryClient cloudinary;
    private final TransactionTemplate transaction;

    public ImageCleanup(ImageAssetRepository assets, CloudinaryClient cloudinary, PlatformTransactionManager manager) {
        this.assets = assets;
        this.cloudinary = cloudinary;
        this.transaction = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelayString = "${campusbridge.images.cleanup-delay-ms:3600000}", initialDelay = 60000)
    public void sweep() {
        if (!cloudinary.configured()) return;
        for (ImageAsset candidate : assets.findTop50ByDeleteAfterLessThanEqualOrderByDeleteAfterAsc(Instant.now())) {
            try {
                transaction.executeWithoutResult(status -> {
                    var asset = assets.lockById(candidate.getId()).orElse(null);
                    if (asset == null || asset.getStudentId() != null || asset.getDeleteAfter() == null
                            || asset.getDeleteAfter().isAfter(Instant.now())) return;
                    cloudinary.delete(asset.getPublicId());
                    assets.delete(asset);
                });
            } catch (RuntimeException ex) {
                log.warn("Image cleanup will retry a pending deletion on the next sweep");
            }
        }
    }
}
