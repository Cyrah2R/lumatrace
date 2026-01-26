package org.lumatrace.cloud.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PhotoRegistrationResponse {

    private UUID photoId;
    private LocalDateTime createdAt;

    // Payload de C2PA / LumaTrace
    private String canonicalJson;
    private String canonicalHash;

    // La semilla matemática derivada para el Core
    private long watermarkSeed;
}