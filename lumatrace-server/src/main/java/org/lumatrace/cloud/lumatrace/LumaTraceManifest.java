package org.lumatrace.cloud.lumatrace;

import java.time.LocalDateTime;
import java.util.UUID;

public record LumaTraceManifest(
        String spec,
        UUID photoId,
        String userId,
        String contentHash,
        String deviceModel,
        LocalDateTime createdAt,
        Double latitude,
        Double longitude
) {}