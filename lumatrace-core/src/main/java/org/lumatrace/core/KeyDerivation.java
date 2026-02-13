package org.lumatrace.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for cryptographically secure seed generation.
 * Ensures consistent seed derivation across Java and Android.
 */
public class KeyDerivation {

    /**
     * Derives a deterministic 64-bit seed from the inputs.
     * Uses SHA-256 hashing to mix the Master Key with context data.
     */
    public static long deriveSeed(long masterKey, String userId, String contentId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Mix inputs
            String rawInput = masterKey + ":" + userId + ":" + contentId;
            byte[] hash = digest.digest(rawInput.getBytes(StandardCharsets.UTF_8));

            // Convert first 8 bytes of hash to long
            long seed = 0;
            for (int i = 0; i < 8; i++) {
                seed = (seed << 8) | (hash[i] & 0xFF);
            }
            return seed;

        } catch (NoSuchAlgorithmException e) {
            // Should never happen on standard JVM/Android
            throw new RuntimeException("CRITICAL: SHA-256 algorithm missing", e);
        }
    }
}