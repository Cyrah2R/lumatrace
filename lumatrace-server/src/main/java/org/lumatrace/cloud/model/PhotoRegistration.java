package org.lumatrace.cloud.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "photo_registrations")
public class PhotoRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String userId;

    @Column(nullable = false)
    private String contentHash;

    private String deviceModel;
    private Double latitude;
    private Double longitude;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PhotoRegistration() {}

    public PhotoRegistration(String userId, String contentHash, String deviceModel, Double latitude, Double longitude) {
        this.userId = userId;
        this.contentHash = contentHash; // 👈 Guardamos el hash
        this.deviceModel = deviceModel;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public String getContentHash() { return contentHash; }
    public String getDeviceModel() { return deviceModel; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}