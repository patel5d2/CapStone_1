package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import com.jonathansoriano.enterprisedevgroupproject.image.CloudinaryClient;
import com.jonathansoriano.enterprisedevgroupproject.image.ImageValidation;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingImageResponse;
import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/marketplace/listing-images")
public class ListingImageController {

    private final CloudinaryClient cloudinary;
    private final ListingImageRepository imageRepository;

    public ListingImageController(
            CloudinaryClient cloudinary,
            ListingImageRepository imageRepository) {

        this.cloudinary = cloudinary;
        this.imageRepository = imageRepository;
    }

    @PostMapping
    @Transactional
    public List<ListingImageResponse> upload(
            @RequestParam(value = "files", required = false)
            List<MultipartFile> files,
            @AuthenticationPrincipal Jwt clerkSession) {

        String owner = CurrentUser.emailOf(clerkSession);

        if (files == null || files.isEmpty() || files.size() > 4) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Select between 1 and 4 photos."
            );
        }

        if (!cloudinary.configured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Photo uploads are not configured."
            );
        }

        List<String> uploadedPublicIds = new ArrayList<>();
        List<ListingImage> uploadedImages = new ArrayList<>();

        try {
            for (MultipartFile file : files) {
                byte[] bytes;

                try (var stream = file.getInputStream()) {
                    bytes = stream.readNBytes(ImageValidation.MAX_BYTES + 1);
                } catch (IOException ex) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Could not read a photo."
                    );
                }

                String mime = ImageValidation.validate(bytes);

                String publicId =
                        "campusbridge/listings/" + UUID.randomUUID();

                String photoUrl = cloudinary.upload(publicId, bytes, mime);

                // Track the Cloudinary asset immediately after upload.
                uploadedPublicIds.add(publicId);

                ListingImage image = new ListingImage();
                image.setOwnerEmail(owner);
                image.setPhotoUrl(photoUrl);
                image.setPublicId(publicId);

                uploadedImages.add(image);
            }

            List<ListingImage> savedImages =
                    imageRepository.saveAllAndFlush(uploadedImages);

            return savedImages.stream()
                    .map(image -> ListingImageResponse.builder()
                            .id(image.getId())
                            .photoUrl(image.getPhotoUrl())
                            .build())
                    .toList();

        } catch (RuntimeException ex) {
            // Compensate for uploads if validation or database persistence fails.
            for (String publicId : uploadedPublicIds) {
                try {
                    cloudinary.delete(publicId);
                } catch (Exception cleanupException) {
                    // Preserve the original upload/persistence exception.
                }
            }

            throw ex;
        }
    }
}
