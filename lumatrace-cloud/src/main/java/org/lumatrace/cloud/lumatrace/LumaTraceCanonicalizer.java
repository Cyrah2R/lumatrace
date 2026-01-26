package org.lumatrace.cloud.lumatrace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class LumaTraceCanonicalizer {

    // Registramos el módulo de fechas para que entienda LocalDateTime
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule()) // <--- ESTA ES LA LÍNEA QUE FALTABA
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static String toCanonicalJson(LumaTraceManifest manifest) {
        try {
            return MAPPER.writeValueAsString(manifest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize manifest", e);
        }
    }

    public static String sha256(String canonicalJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }
}