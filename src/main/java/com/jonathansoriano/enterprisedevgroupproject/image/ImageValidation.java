package com.jonathansoriano.enterprisedevgroupproject.image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Header/dimension checks precede provider I/O and never trust the supplied MIME type. */
public final class ImageValidation {
    public static final int MAX_BYTES = 10_000_000;
    public static final int MAX_DIMENSION = 4000;
    private ImageValidation() {}

    public static String validate(byte[] bytes) {
        if (bytes.length == 0) throw bad("Choose a photo first.");
        if (bytes.length > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                "Photo must be 10 MB or smaller.");
        String type;
        int width;
        int height;
        try {
            if (bytes.length >= 30 && ascii(bytes, 0, 4).equals("RIFF")
                    && ascii(bytes, 8, 4).equals("WEBP")) {
                type = "image/webp";
                String chunk = ascii(bytes, 12, 4);
                if (chunk.equals("VP8X")) {
                    width = 1 + little(bytes, 24, 3);
                    height = 1 + little(bytes, 27, 3);
                } else if (chunk.equals("VP8L") && (bytes[20] & 255) == 0x2f) {
                    int bits = little(bytes, 21, 4);
                    width = (bits & 0x3fff) + 1;
                    height = ((bits >>> 14) & 0x3fff) + 1;
                } else if (chunk.equals("VP8 ") && (bytes[23] & 255) == 0x9d
                        && (bytes[24] & 255) == 0x01 && (bytes[25] & 255) == 0x2a) {
                    width = little(bytes, 26, 2) & 0x3fff;
                    height = little(bytes, 28, 2) & 0x3fff;
                } else throw bad("Choose a valid JPEG, PNG or WebP photo.");
            } else {
                boolean jpeg = bytes.length > 3 && (bytes[0] & 255) == 255
                        && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
                boolean png = bytes.length > 8 && (bytes[0] & 255) == 137
                        && ascii(bytes, 1, 3).equals("PNG") && bytes[4] == 13
                        && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
                if (!jpeg && !png) throw bad("Choose a JPEG, PNG or WebP photo. HEIC and SVG are not supported.");
                type = jpeg ? "image/jpeg" : "image/png";
                try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers = ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) throw bad("This photo could not be read. Choose another image.");
                    var reader = readers.next();
                    try {
                        reader.setInput(input);
                        width = reader.getWidth(0);
                        height = reader.getHeight(0);
                    } finally { reader.dispose(); }
                }
            }
        } catch (IOException | IndexOutOfBoundsException ex) {
            throw bad("This photo could not be read. Choose another image.");
        }
        if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION)
            throw bad("Photo dimensions must be no larger than 4000 × 4000 pixels.");
        return type;
    }

    private static String ascii(byte[] b, int at, int length) {
        return new String(b, at, length, StandardCharsets.US_ASCII);
    }
    private static int little(byte[] b, int at, int count) {
        int value = 0;
        for (int i = 0; i < count; i++) value |= (b[at + i] & 255) << (i * 8);
        return value;
    }
    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
