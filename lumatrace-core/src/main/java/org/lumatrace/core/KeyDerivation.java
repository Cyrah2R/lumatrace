package org.lumatrace.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class KeyDerivation {

    public static long deriveSeed(
            long masterKey,
            String userId,
            String contentId
    ) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");

            sha.update(ByteBuffer.allocate(8).putLong(masterKey).array());
            sha.update(userId.getBytes(StandardCharsets.UTF_8));
            sha.update(contentId.getBytes(StandardCharsets.UTF_8));

            byte[] hash = sha.digest();

            // Fold first 8 bytes into a long
            ByteBuffer buf = ByteBuffer.wrap(hash);
            return buf.getLong();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
