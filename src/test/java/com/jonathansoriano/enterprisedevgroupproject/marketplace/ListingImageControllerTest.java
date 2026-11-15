package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import com.jonathansoriano.enterprisedevgroupproject.config.SecurityConfig;
import com.jonathansoriano.enterprisedevgroupproject.image.CloudinaryClient;
import com.jonathansoriano.enterprisedevgroupproject.security.ClerkJwtAuthenticationConverter;
import com.jonathansoriano.enterprisedevgroupproject.security.InstitutionalAccessDeniedHandler;
import com.jonathansoriano.enterprisedevgroupproject.security.InstitutionalAccessPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = ListingImageController.class)
@Import({SecurityConfig.class, InstitutionalAccessPolicy.class,
        ClerkJwtAuthenticationConverter.class, InstitutionalAccessDeniedHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class ListingImageControllerTest {

    private static final String ENDPOINT = "/api/marketplace/listing-images";

    @MockitoBean
    private CloudinaryClient cloudinary;

    @MockitoBean
    private ListingImageRepository imageRepository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void signIn() {
        Jwt clerkSession = Jwt.withTokenValue("clerk-session-token")
                .header("alg", "RS256")
                .claim("sub", "user_test")
                .claim("email", "seller@example.com")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(clerkSession));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadOneValidImage_ReturnsUrlAndCallsCloudinary() throws Exception {
        byte[] image = png();
        when(cloudinary.configured()).thenReturn(true);
        when(cloudinary.upload(anyString(), any(byte[].class), eq("image/png")))
                .thenAnswer(invocation -> "https://images.example.test/"
                        + invocation.getArgument(0, String.class) + ".webp");
        when(imageRepository.saveAllAndFlush(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(multipart(ENDPOINT)
                        .file(new MockMultipartFile("files", "listing.png", "text/plain", image)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].photoUrl").value(
                        org.hamcrest.Matchers.startsWith("https://images.example.test/")));

        verifyUploads(List.of(image));
    }

    @Test
    void uploadFourValidImages_ReturnsFourUrlsAndCallsCloudinaryFourTimes() throws Exception {
        byte[] image = png();
        when(cloudinary.configured()).thenReturn(true);
        when(cloudinary.upload(anyString(), any(byte[].class), eq("image/png")))
                .thenAnswer(invocation -> "https://images.example.test/"
                        + invocation.getArgument(0, String.class) + ".webp");
        when(imageRepository.saveAllAndFlush(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartHttpServletRequestBuilder request = multipart(ENDPOINT);
        for (int i = 1; i <= 4; i++) {
            request.file(new MockMultipartFile("files", "listing-" + i + ".png", "image/png", image));
        }

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].photoUrl").isNotEmpty())
                .andExpect(jsonPath("$[3].photoUrl").isNotEmpty());

        verifyUploads(List.of(image, image, image, image));
    }

    @Test
    void uploadFiveImages_IsRejectedWithoutCallingCloudinary() throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(ENDPOINT);
        for (int i = 1; i <= 5; i++) {
            request.file(new MockMultipartFile("files", "listing-" + i + ".png", "image/png", png()));
        }

        mockMvc.perform(request).andExpect(status().isBadRequest());

        verifyNoInteractions(cloudinary);
        verifyNoInteractions(imageRepository);
    }

    @Test
    void uploadNoFiles_IsRejectedWithoutCallingCloudinary() throws Exception {
        mockMvc.perform(multipart(ENDPOINT)).andExpect(status().isBadRequest());

        verifyNoInteractions(cloudinary);
        verifyNoInteractions(imageRepository);
    }

    @Test
    void uploadInvalidImage_IsRejectedWithoutUploading() throws Exception {
        when(cloudinary.configured()).thenReturn(true);

        mockMvc.perform(multipart(ENDPOINT)
                        .file(new MockMultipartFile("files", "not-an-image.png", "image/png",
                                "<html>not an image</html>".getBytes())))
                .andExpect(status().isBadRequest());

        verify(cloudinary).configured();
        verify(cloudinary, never()).upload(anyString(), any(byte[].class), anyString());
        verifyNoInteractions(imageRepository);
    }

    @Test
    void uploadWhenCloudinaryIsNotConfigured_ReturnsServiceUnavailable() throws Exception {
        when(cloudinary.configured()).thenReturn(false);

        mockMvc.perform(multipart(ENDPOINT)
                        .file(new MockMultipartFile("files", "listing.png", "image/png", png())))
                .andExpect(status().isServiceUnavailable());

        verify(cloudinary).configured();
        verify(cloudinary, never()).upload(anyString(), any(byte[].class), anyString());
        verifyNoInteractions(imageRepository);
    }

    private void verifyUploads(List<byte[]> expectedImages) {
        var imageCaptor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        var publicIdCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        var mimeCaptor = org.mockito.ArgumentCaptor.forClass(String.class);

        verify(cloudinary, times(expectedImages.size()))
                .upload(publicIdCaptor.capture(), imageCaptor.capture(), mimeCaptor.capture());
        assertThat(publicIdCaptor.getAllValues())
                .allMatch(id -> id.matches("campusbridge/listings/[0-9a-fA-F-]{36}"))
                .doesNotHaveDuplicates();
        assertThat(mimeCaptor.getAllValues()).containsOnly("image/png");
        assertThat(imageCaptor.getAllValues()).hasSize(expectedImages.size());
        for (int i = 0; i < expectedImages.size(); i++) {
            assertArrayEquals(expectedImages.get(i), imageCaptor.getAllValues().get(i));
        }
    }

    private static byte[] png() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }
}
