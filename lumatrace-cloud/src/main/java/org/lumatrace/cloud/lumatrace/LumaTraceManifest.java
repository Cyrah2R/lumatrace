package org.lumatrace.cloud.lumatrace;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class LumaTraceManifest {

    private String spec;          // "lumatrace/1.0"
    private UUID photoId;
    private String userId;
    private String deviceModel;
    private LocalDateTime createdAt;
    private Double latitude;
    private Double longitude;
}
