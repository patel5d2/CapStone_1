package com.jonathansoriano.enterprisedevgroupproject.image;

import java.io.IOException;
import java.time.Instant;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileImageService {
    private final ImageAssetRepository assets;
    private final CloudinaryClient cloudinary;

    public ProfileImageService(ImageAssetRepository assets, CloudinaryClient cloudinary) {
        this.assets = assets;
        this.cloudinary = cloudinary;
    }

    // Intentionally no surrounding transaction: the pending provider id must commit
    // before the network call, including when an upload times out after succeeding.
    public String upload(String subject, MultipartFile file) {
        byte[] bytes;
        try (var stream = file.getInputStream()) {
            bytes = stream.readNBytes(ImageValidation.MAX_BYTES + 1);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read this photo. Choose it again.");
        }
        String mime = ImageValidation.validate(bytes);
        if (!cloudinary.configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Photo uploads are not configured yet. Save the other fields and try the photo later.");
        ImageAsset asset = new ImageAsset();
        asset.setId(UUID.randomUUID().toString());
        asset.setOwnerSubject(subject);
        asset.setPublicId("campusbridge/profiles/" + asset.getId());
        asset.setDeleteAfter(Instant.now().plus(Duration.ofHours(24)));
        assets.saveAndFlush(asset);
        asset.setUrl(cloudinary.upload(asset.getPublicId(), bytes, mime));
        assets.saveAndFlush(asset);
        return asset.getUrl();
    }

    /** Called inside the profile transaction: nobody can attach another user's upload. */
    @Transactional
    public void attach(String subject, Long studentId, String url) {
        ImageAsset next = null;
        if (url != null && !url.isBlank()) {
            var candidate = assets.findByUrl(url).orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Upload your photo here before saving it."));
            next = assets.lockById(candidate.getId()).orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.CONFLICT, "This upload expired. Upload the photo again."));
            if (!next.getOwnerSubject().equals(subject)) throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "This photo belongs to another account.");
            if (next.getStudentId() != null && !next.getStudentId().equals(studentId))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This photo is already attached elsewhere.");
        }
        for (ImageAsset old : assets.findByStudentId(studentId)) {
            if (!Objects.equals(old.getUrl(), url)) {
                old.setStudentId(null);
                old.setDeleteAfter(Instant.now());
                assets.save(old);
            }
        }
        if (next != null) {
            next.setStudentId(studentId);
            next.setDeleteAfter(null);
            assets.save(next);
        }
    }
}
