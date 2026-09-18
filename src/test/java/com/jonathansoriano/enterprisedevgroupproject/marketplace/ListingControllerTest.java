package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import com.jonathansoriano.enterprisedevgroupproject.config.SecurityConfig;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import com.jonathansoriano.enterprisedevgroupproject.security.ClerkJwtAuthenticationConverter;
import com.jonathansoriano.enterprisedevgroupproject.security.InstitutionalAccessDeniedHandler;
import com.jonathansoriano.enterprisedevgroupproject.security.InstitutionalAccessPolicy;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = ListingController.class)
// SecurityConfig now depends on the objective 1 access rule, and a @WebMvcTest slice
// does not pick up @Component beans on its own. Filters are still off below: these
// tests are about the controllers, and the rule has its own test.
@Import({SecurityConfig.class, InstitutionalAccessPolicy.class,
        ClerkJwtAuthenticationConverter.class, InstitutionalAccessDeniedHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class ListingControllerTest {

    private static final String SIGNED_IN_EMAIL = "buyer@example.com";

    @MockitoBean
    private ListingService listingService;
    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void signIn() {
        Jwt clerkSession = Jwt.withTokenValue("clerk-session-token")
                .header("alg", "RS256")
                .claim("sub", "user_test")
                .claim("email", SIGNED_IN_EMAIL)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(clerkSession));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void search_Http200() throws Exception {
        ListingResponse listing = ListingResponse.builder()
                .id(1L)
                .sellerEmail("seller@example.com")
                .title("Calculus Textbook")
                .category(ListingCategory.BOOKS)
                .listingType(ListingType.SELL)
                .status(ListingStatus.AVAILABLE)
                .price(new BigDecimal("40.00"))
                .build();
        when(listingService.search(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(listing));

        mockMvc.perform(get("/api/marketplace/listings").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Calculus Textbook"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void create_Http201_Created() throws Exception {
        ListingResponse created = ListingResponse.builder().id(5L).title("Mini Fridge").build();
        when(listingService.create(any(), any())).thenReturn(created);

        String requestJson = """
                {
                  "title": "Mini Fridge",
                  "description": "Works great, moving out",
                  "category": "FURNITURE",
                  "listingType": "SELL",
                  "price": 50.00
                }
                """;

        mockMvc.perform(post("/api/marketplace/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Mini Fridge"));
    }

    @Test
    void create_AcceptsHttpsPhotoUrls() throws Exception {
        ListingResponse created = ListingResponse.builder().id(5L).title("Mini Fridge").build();
        when(listingService.create(any(), any())).thenReturn(created);

        String requestJson = """
                {
                  "title": "Mini Fridge",
                  "category": "FURNITURE",
                  "listingType": "SELL",
                  "photoUrls": ["https://example.test/a.jpg", "https://example.test/b.jpg"]
                }
                """;

        mockMvc.perform(post("/api/marketplace/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());
    }

    @Test
    void create_RejectsMoreThanFivePhotos_Http400() throws Exception {
        // Objective 3 caps a listing at five photos.
        String requestJson = """
                {
                  "title": "Mini Fridge",
                  "category": "FURNITURE",
                  "listingType": "SELL",
                  "photoUrls": ["https://example.test/1.jpg", "https://example.test/2.jpg",
                                "https://example.test/3.jpg", "https://example.test/4.jpg",
                                "https://example.test/5.jpg", "https://example.test/6.jpg"]
                }
                """;

        mockMvc.perform(post("/api/marketplace/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_ValidationFailure_ReportsEachFieldSeparately() throws Exception {
        // The response has to say WHICH field, in a form a client can act on. It used to
        // carry a Java map's toString() inside the message, so a student read
        // "{title=Title is required}" and the composer could not mark the offending input.
        String invalidJson = """
                {
                  "listingType": "SELL",
                  "photoUrls": ["http://tracker.test/pixel.gif"]
                }
                """;

        mockMvc.perform(post("/api/marketplace/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"))
                .andExpect(jsonPath("$.fieldErrors.category").value("Category is required"))
                .andExpect(jsonPath("$.fieldErrors['photoUrls[0]']").value("Each photo must be an https:// web address"))
                // ...and the human-readable message is a sentence, not a map literal.
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("{"))));
    }

    @Test
    void create_RejectsAPhotoUrlThatIsNotHttps_Http400() throws Exception {
        // The reason this bound exists: an arbitrary address is served to every viewer of
        // the listing, so it can be used to collect their IP addresses.
        String requestJson = """
                {
                  "title": "Mini Fridge",
                  "category": "FURNITURE",
                  "listingType": "SELL",
                  "photoUrls": ["http://tracker.test/pixel.gif?listing=1"]
                }
                """;

        mockMvc.perform(post("/api/marketplace/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_RejectsANonUrlPhotoValue_Http400() throws Exception {
        String requestJson = """
                {
                  "title": "Mini Fridge",
                  "category": "FURNITURE",
                  "listingType": "SELL",
                  "photoUrls": ["javascript:alert(1)"]
                }
                """;

        mockMvc.perform(post("/api/marketplace/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_RejectsAnUnboundedPhotoUrl_Http400() throws Exception {
        // The same DTO backs update, so the bound has to hold on both paths — a listing
        // that passed validation at creation must not be editable into a bad state.
        String requestJson = """
                {
                  "title": "Mini Fridge",
                  "category": "FURNITURE",
                  "listingType": "SELL",
                  "photoUrls": ["http://tracker.test/pixel.gif"]
                }
                """;

        mockMvc.perform(put("/api/marketplace/listings/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_MissingRequiredField_Http400() throws Exception {
        String invalidJson = """
                {
                  "description": "Missing title and category"
                }
                """;

        mockMvc.perform(post("/api/marketplace/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
