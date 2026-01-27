package org.lumatrace.cloud.lumatrace;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable record for content provenance data.
 */
public record LumaTraceManifest(
        String spec,
        UUID photoId,
        String userId,
        String deviceModel,
        LocalDateTime createdAt,
        Double latitude,
        Double longitude
) {}
