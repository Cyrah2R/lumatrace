package org.lumatrace.cloud.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Certificate response containing the generated seed and manifest hash.
 */
public class PhotoRegistrationResponse {

    private final UUID photoId;
    private final LocalDateTime createdAt;
    private final String canonicalJson;
    private final String canonicalHash;
    private final long watermarkSeed;

    public PhotoRegistrationResponse(UUID photoId, LocalDateTime createdAt, String canonicalJson, String canonicalHash, long watermarkSeed) {
        this.photoId = photoId;
        this.createdAt = createdAt;
        this.canonicalJson = canonicalJson;
        this.canonicalHash = canonicalHash;
        this.watermarkSeed = watermarkSeed;
    }

    public UUID getPhotoId() { return photoId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getCanonicalJson() { return canonicalJson; }
    public String getCanonicalHash() { return canonicalHash; }
    public long getWatermarkSeed() { return watermarkSeed; }
}