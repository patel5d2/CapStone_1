
package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import com.jonathansoriano.enterprisedevgroupproject.image.CloudinaryClient;
import com.jonathansoriano.enterprisedevgroupproject.image.ImageValidation;
import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

    public ListingImageController(CloudinaryClient cloudinary) {
        this.cloudinary = cloudinary;
    }

    @PostMapping
    public List<String> upload(
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal Jwt clerkSession) {

        String owner = CurrentUser.emailOf(clerkSession);

        if (files == null || files.isEmpty() || files.size() > 4) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Select between 1 and 4 photos.");
        }

        if (!cloudinary.configured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Photo uploads are not configured.");
        }

        List<String> urls = new ArrayList<>();

        for (MultipartFile file : files) {
            byte[] bytes;
            try (var stream = file.getInputStream()) {
                bytes = stream.readNBytes(ImageValidation.MAX_BYTES + 1);
            } catch (IOException ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Could not read a photo.");
            }

            String mime = ImageValidation.validate(bytes);
            String publicId = "campusbridge/listings/" +
                    UUID.randomUUID();

            urls.add(cloudinary.upload(publicId, bytes, mime));
        }

        return urls;
    }
}
