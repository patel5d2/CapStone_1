package com.jonathansoriano.enterprisedevgroupproject.marketplace.dto;

import com.jonathansoriano.enterprisedevgroupproject.marketplace.ListingCategory;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.ListingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ListingRequest {

    /** Objective 3: "up to 5 photos". Fixed by the contract, not a tunable. */
    public static final int MAX_PHOTOS = 5;

    /**
     * {@code https} only, and no whitespace, quote or angle bracket — those are what turn
     * a stored address into something other than an address when it reaches a template or
     * a log line. A host allowlist replaces this in S1-05.
     */
    static final String PHOTO_URL_PATTERN = "^https://[^\\s\"'<>`]+$";

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Category is required")
    private ListingCategory category;

    @NotNull(message = "Listing type is required")
    private ListingType listingType;

    private BigDecimal price;

    private String courseCode;

    private Long schoolId;

    /**
     * Photo references for the listing.
     *
     * <p>These are stored as sent and served to every viewer, so an unvalidated list lets
     * a signed-in student point a listing at any URL on the internet — most usefully at
     * something that records the IP of everyone who opens the listing. The bounds here
     * are the provider-independent half of that fix: a count matching objective 3, an
     * {@code https} scheme, and a length the {@code listing_photo.photo_url} column can
     * actually hold.
     *
     * <p><b>This is not yet the ownership constraint.</b> Restricting references to the
     * approved asset host, so a caller cannot attach an asset they do not own, needs the
     * host — that is S1-05, once D-IMAGES names a provider. Until then any {@code https}
     * URL is accepted. See {@code docs/phase-1/image-storage.md}.
     */
    @Size(max = MAX_PHOTOS, message = "A listing can have at most " + MAX_PHOTOS + " photos")
    private List<
            @Pattern(regexp = PHOTO_URL_PATTERN, message = "Each photo must be an https:// web address")
            @Size(max = 255, message = "A photo address must be 255 characters or fewer")
            String> photoUrls;
}
