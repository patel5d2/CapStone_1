package com.jonathansoriano.enterprisedevgroupproject.image;

import java.net.URI;
import java.net.http.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

/** Provider secrets never leave this server. No SDK, redirects, or client-supplied URLs. */
@Component
public class CloudinaryClient {
    private final String configuration;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public CloudinaryClient(@Value("${CLOUDINARY_URL:}") String configuration, ObjectMapper mapper) {
        this.configuration = configuration;
        this.mapper = mapper;
    }

    public boolean configured() { return !configuration.isBlank(); }

    public String upload(String publicId, byte[] bytes, String mime) {
        var params = new TreeMap<String, String>();
        params.put("public_id", publicId);
        params.put("overwrite", "false");
        params.put("transformation", "c_limit,h_1600,w_1600/fl_strip_profile,f_webp");
        JsonNode response = call("upload", params, "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes));
        String url = response.path("secure_url").asText();
        String cloud = configuration().getHost();
        if (!publicId.equals(response.path("public_id").asText())
                || !url.startsWith("https://res.cloudinary.com/" + cloud + "/image/upload/")
                || !url.endsWith("/" + publicId + ".webp") || url.length() > 255
                || !response.path("format").asText().equals("webp")
                || response.path("width").asInt() < 1 || response.path("width").asInt() > 1600
                || response.path("height").asInt() < 1 || response.path("height").asInt() > 1600) {
            throw unavailable();
        }
        return url;
    }

    public void delete(String publicId) {
        var params = new TreeMap<String, String>();
        params.put("public_id", publicId);
        params.put("invalidate", "true");
        String result = call("destroy", params, null).path("result").asText();
        if (!result.equals("ok") && !result.equals("not found")) throw unavailable();
    }

    private URI configuration() {
        try {
            URI uri = URI.create(configuration);
            if (!"cloudinary".equals(uri.getScheme()) || uri.getHost() == null
                    || !uri.getHost().matches("[a-z0-9-]+") || uri.getUserInfo() == null
                    || uri.getUserInfo().split(":", 2).length != 2) throw new IllegalArgumentException();
            return uri;
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Photo uploads are not configured yet. You can save the other profile fields and try the photo later.");
        }
    }

    private JsonNode call(String operation, TreeMap<String, String> params, String file) {
        URI config = configuration();
        String[] credentials = config.getUserInfo().split(":", 2);
        params.put("timestamp", Long.toString(Instant.now().getEpochSecond()));
        try {
            String canonical = params.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("&"));
            String signature = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((canonical + credentials[1]).getBytes(StandardCharsets.UTF_8)));
            var body = new TreeMap<>(params);
            body.put("signature", signature);
            body.put("api_key", credentials[0]);
            if (file != null) body.put("file", file);
            String encoded = body.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .collect(Collectors.joining("&"));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.cloudinary.com/v1_1/"
                            + config.getHost() + "/image/" + operation))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(encoded)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw unavailable();
            return mapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception ex) {
            // Do not propagate provider responses, request bodies, config, or signatures
            // into the global exception logger or the API response.
            throw unavailable();
        }
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Photo service is unavailable. Please try the upload again.");
    }
}
