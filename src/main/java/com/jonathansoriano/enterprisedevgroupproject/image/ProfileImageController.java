package com.jonathansoriano.enterprisedevgroupproject.image;

import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile-images")
public class ProfileImageController {
    private final ProfileImageService images;
    public ProfileImageController(ProfileImageService images) { this.images = images; }

    public record UploadResponse(String photoUrl) {}

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse upload(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
        return new UploadResponse(images.upload(CurrentUser.subjectOf(jwt), file));
    }
}
