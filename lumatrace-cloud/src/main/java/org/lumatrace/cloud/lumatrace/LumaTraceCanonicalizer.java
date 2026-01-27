package org.lumatrace.cloud.lumatrace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Provides deterministic JSON serialization for manifest integrity.
 */
public final class LumaTraceCanonicalizer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private LumaTraceCanonicalizer() {}

    public static String toCanonicalJson(LumaTraceManifest manifest) {
        try {
            return MAPPER.writeValueAsString(manifest);
        } catch (Exception e) {
            throw new IllegalStateException("Canonical serialization failed", e);
        }
    }

    public static String sha256(String canonicalJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append("%02x".formatted(b));
            }
            return sb.toString();

        } catch (Exception e) {
            throw new IllegalStateException("Hashing failed", e);
        }
    }
}
