package com.jonathansoriano.enterprisedevgroupproject.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class ImageValidationTest {
    private byte[] png(int width, int height) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
    @Test void validImageIsRecognizedByContent() throws Exception {
        assertEquals("image/png", ImageValidation.validate(png(40, 40)));
    }
    @Test void htmlRenamedAsPhotoIsRejected() {
        assertThrows(ResponseStatusException.class, () -> ImageValidation.validate("<html>not a photo</html>".getBytes()));
    }
    @Test void oversizedAndEmptyFilesAreRejected() {
        var error = assertThrows(ResponseStatusException.class,
                () -> ImageValidation.validate(new byte[ImageValidation.MAX_BYTES + 1]));
        assertEquals(413, error.getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> ImageValidation.validate(new byte[0]));
    }
    @Test void dimensionLimitIsCheckedBeforeDecode() throws Exception {
        assertThrows(ResponseStatusException.class, () -> ImageValidation.validate(png(4001, 1)));
    }
}
