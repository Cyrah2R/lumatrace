package org.lumatrace.cloud.controller;

import org.lumatrace.cloud.dto.PhotoRegistrationRequest;
import org.lumatrace.cloud.dto.PhotoRegistrationResponse;
import org.lumatrace.cloud.lumatrace.LumaTraceCanonicalizer;
import org.lumatrace.cloud.lumatrace.LumaTraceManifest;
import org.lumatrace.cloud.model.PhotoRegistration;
import org.lumatrace.cloud.repository.PhotoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/photos")
public class RegistrationController {

    private final PhotoRepository repository;

    public RegistrationController(PhotoRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/register")
    public ResponseEntity<PhotoRegistrationResponse> registerPhoto(
            @RequestBody PhotoRegistrationRequest request
    ) {
        // 1️⃣ Crear entidad interna
        PhotoRegistration entity = new PhotoRegistration();
        entity.setUserId(request.getUserId());
        entity.setDeviceModel(request.getDeviceModel());
        entity.setLatitude(request.getLatitude());
        entity.setLongitude(request.getLongitude());

        PhotoRegistration saved = repository.save(entity);

        // 2️⃣ Crear manifiesto LumaTrace
        LumaTraceManifest manifest = LumaTraceManifest.builder()
                .spec("lumatrace/1.0")
                .photoId(saved.getId())
                .userId(saved.getUserId())
                .deviceModel(saved.getDeviceModel())
                .createdAt(saved.getCreatedAt())
                .latitude(saved.getLatitude())
                .longitude(saved.getLongitude())
                .build();

        String canonicalJson = LumaTraceCanonicalizer.toCanonicalJson(manifest);
        String hash = LumaTraceCanonicalizer.sha256(canonicalJson);

        // 3️⃣ Respuesta al móvil
        PhotoRegistrationResponse response =
                PhotoRegistrationResponse.builder()
                        .photoId(saved.getId())
                        .createdAt(saved.getCreatedAt())
                        .canonicalJson(canonicalJson)
                        .canonicalHash(hash)
                        .build();

        return ResponseEntity.ok(response);
    }
}
