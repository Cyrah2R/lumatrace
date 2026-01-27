package org.lumatrace.cloud.dto;

import jakarta.validation.constraints.NotBlank;

public class PhotoRegistrationRequest {

    @NotBlank
    private String userId;

    private String deviceModel;
    private Double latitude;
    private Double longitude;

    public String getUserId() {
        return userId;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}
