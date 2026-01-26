package org.lumatrace.cloud.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PhotoRegistrationRequest {

    @NotBlank
    private String userId;

    private String deviceModel;

    private Double latitude;
    private Double longitude;
}
