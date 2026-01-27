package org.lumatrace.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class KeyDerivation {
    public static long deriveSeed(long masterKey, String userId, String contentId) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(ByteBuffer.allocate(8).putLong(masterKey).array());
            sha.update(userId.getBytes(StandardCharsets.UTF_8));
            sha.update(contentId.getBytes(StandardCharsets.UTF_8));
            byte[] hash = sha.digest();

            // Entropy folding (XOR de los 4 cuadrantes) para máxima seguridad
            long q1 = ByteBuffer.wrap(hash, 0, 8).getLong();
            long q2 = ByteBuffer.wrap(hash, 8, 8).getLong();
            long q3 = ByteBuffer.wrap(hash, 16, 8).getLong();
            long q4 = ByteBuffer.wrap(hash, 24, 8).getLong();
            return q1 ^ q2 ^ q3 ^ q4;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}