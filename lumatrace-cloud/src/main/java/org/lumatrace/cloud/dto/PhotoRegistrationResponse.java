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

    // Payload lógico
    private String canonicalJson;
    private String canonicalHash;
}
