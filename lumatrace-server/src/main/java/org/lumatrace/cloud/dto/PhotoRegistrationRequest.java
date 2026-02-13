package org.lumatrace.cloud.dto;

import jakarta.validation.constraints.NotNull;

public class PhotoRegistrationRequest {

    @NotNull
    private String userId;

    @NotNull
    private String contentHash;

    @NotNull
    private String deviceModel;

    private double latitude;
    private double longitude;

    public PhotoRegistrationRequest() {
    }

    public PhotoRegistrationRequest(String userId, String contentHash, String deviceModel, double latitude, double longitude) {
        this.userId = userId;
        this.contentHash = contentHash;
        this.deviceModel = deviceModel;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getUserId() { return userId; }
    public String getContentHash() { return contentHash; }
    public String getDeviceModel() { return deviceModel; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
}