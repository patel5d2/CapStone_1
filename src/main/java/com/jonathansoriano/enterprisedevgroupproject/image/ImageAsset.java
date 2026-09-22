package com.jonathansoriano.enterprisedevgroupproject.image;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "image_asset")
@Getter
@Setter
@NoArgsConstructor
public class ImageAsset {
    @Id private String id;
    private String ownerSubject;
    private String publicId;
    private String url;
    private Long studentId;
    private Instant deleteAfter;
}
