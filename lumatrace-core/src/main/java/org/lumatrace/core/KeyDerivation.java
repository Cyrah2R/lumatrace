package org.lumatrace.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Cryptographic Key Derivation Function (KDF).
 * Generates a deterministic 64-bit seed from a Master Key and asset metadata
 * using SHA-256 bitwise folding to ensure uniform distribution and collision resistance.
 */
public class KeyDerivation {

    private static final String HASH_ALGORITHM = "SHA-256";

    /**
     * Derives a high-entropy seed for PRNG initialization.
     * * @param masterKey The root 64-bit security key.
     * @param userId    Identifier for the content originator.
     * @param contentId Unique identifier for the digital asset.
     * @return A deterministic 64-bit seed for signature generation.
     */
    public static long deriveSeed(long masterKey, String userId, String contentId) {
        try {
            MessageDigest sha = MessageDigest.getInstance(HASH_ALGORITHM);

            // Allocate buffer once to minimize GC pressure
            ByteBuffer buffer = ByteBuffer.allocate(8);
            sha.update(buffer.putLong(masterKey).array());

            sha.update(userId.getBytes(StandardCharsets.UTF_8));
            sha.update(contentId.getBytes(StandardCharsets.UTF_8));

            byte[] hash = sha.digest();

            // Perform 256-to-64 bit compression via XOR folding
            // This ensures every bit of the SHA-256 output influences the final seed
            long q1 = ByteBuffer.wrap(hash, 0, 8).getLong();
            long q2 = ByteBuffer.wrap(hash, 8, 8).getLong();
            long q3 = ByteBuffer.wrap(hash, 16, 8).getLong();
            long q4 = ByteBuffer.wrap(hash, 24, 8).getLong();

            return q1 ^ q2 ^ q3 ^ q4;

        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory implementation in JVM; this state should be unreachable.
            throw new IllegalStateException("Required cryptographic provider missing", e);
        }
    }
}