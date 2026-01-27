package org.lumatrace.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Cryptographic Key Derivation Function (KDF) for LumaTrace.
 *
 * Features:
 * 1. Deterministic seed generation from multiple inputs
 * 2. Full 256-bit entropy folding into 64-bit seed
 * 3. Collision resistance with field separation
 * 4. Algorithm agility (prepared for future hash functions)
 * 5. Zero allocations in hot path (after JIT compilation)
 *
 * Security notes:
 * - Uses SHA-256, which is considered secure for KDF in this context
 * - Includes domain separation to prevent hash misuse
 * - The 64-bit seed is sufficient for watermarking, but not for cryptographic keys
 */
public class KeyDerivation {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String DOMAIN_SEPARATOR = "LumaTraceWatermark";
    private static final byte VERSION = 1;

    /**
     * Derives a deterministic 64-bit seed from the given inputs.
     * The seed is derived using SHA-256 with domain separation and entropy folding.
     *
     * @param masterKey The master cryptographic key (64-bit)
     * @param userId User identifier (cannot be null)
     * @param contentId Content identifier (cannot be null)
     * @return A 64-bit seed suitable for PRNG initialization
     * @throws NullPointerException if userId or contentId is null
     */
    public static long deriveSeed(long masterKey, String userId, String contentId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(contentId, "contentId cannot be null");

        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);

            // Domain separation and versioning
            digest.update(DOMAIN_SEPARATOR.getBytes(StandardCharsets.UTF_8));
            digest.update(VERSION);

            // Master key (64-bit, big-endian)
            updateLong(digest, masterKey);

            // User ID with length prefix (prevents concatenation collisions)
            updateStringWithLength(digest, userId);

            // Content ID with length prefix
            updateStringWithLength(digest, contentId);

            byte[] hash = digest.digest(); // 32 bytes (256 bits)

            // Entropy folding: XOR all 4 quadrants of the hash
            return foldHashToLong(hash);

        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by Java SE, so this should never happen
            throw new SecurityException("Critical: SHA-256 algorithm unavailable", e);
        }
    }

    /**
     * Updates digest with a 64-bit long in big-endian order.
     * This method is allocation-free after JIT compilation.
     */
    private static void updateLong(MessageDigest digest, long value) {
        // Big-endian: most significant byte first
        digest.update((byte) (value >>> 56));
        digest.update((byte) (value >>> 48));
        digest.update((byte) (value >>> 40));
        digest.update((byte) (value >>> 32));
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    /**
     * Updates digest with a UTF-8 string, prefixed with its length.
     * The length is encoded as a 32-bit big-endian integer.
     * This prevents collisions like ("ab", "c") vs ("a", "bc").
     */
    private static void updateStringWithLength(MessageDigest digest, String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);

        // Include length prefix (32-bit big-endian)
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);

        digest.update(bytes);
    }

    /**
     * Folds a 32-byte hash into a 64-bit long by XORing its 8-byte quadrants.
     * This preserves entropy from the entire hash while reducing to 64 bits.
     *
     * Mathematical property: If the hash is uniformly random, the folded
     * value is also uniformly random across 64 bits.
     */
    private static long foldHashToLong(byte[] hash) {
        // SHA-256 produces 32 bytes, so we have 4 quadrants of 8 bytes each
        long q1 = bytesToLong(hash, 0);
        long q2 = bytesToLong(hash, 8);
        long q3 = bytesToLong(hash, 16);
        long q4 = bytesToLong(hash, 24);

        return q1 ^ q2 ^ q3 ^ q4;
    }

    /**
     * Converts 8 bytes from the specified offset into a 64-bit long (big-endian).
     * The method is safe only if the array has at least offset+8 bytes.
     * After JIT compilation, this method should be allocation-free and inlined.
     */
    private static long bytesToLong(byte[] bytes, int offset) {
        // Use '& 0xFF' to avoid sign extension when converting byte to long
        return ((long) (bytes[offset]     & 0xFF) << 56) |
                ((long) (bytes[offset + 1] & 0xFF) << 48) |
                ((long) (bytes[offset + 2] & 0xFF) << 40) |
                ((long) (bytes[offset + 3] & 0xFF) << 32) |
                ((long) (bytes[offset + 4] & 0xFF) << 24) |
                ((long) (bytes[offset + 5] & 0xFF) << 16) |
                ((long) (bytes[offset + 6] & 0xFF) <<  8) |
                ((long) (bytes[offset + 7] & 0xFF));
    }

    /**
     * Returns the algorithm name for logging and debugging.
     */
    public static String getAlgorithm() {
        return HASH_ALGORITHM;
    }

    /**
     * Returns the domain separator for testing purposes.
     */
    static String getDomainSeparator() {
        return DOMAIN_SEPARATOR;
    }

    /**
     * Returns the version for testing purposes.
     */
    static byte getVersion() {
        return VERSION;
    }
}