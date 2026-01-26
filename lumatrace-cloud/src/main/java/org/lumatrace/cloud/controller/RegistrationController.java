package org.lumatrace.cloud.controller;

import org.lumatrace.cloud.dto.PhotoRegistrationRequest;
import org.lumatrace.cloud.dto.PhotoRegistrationResponse;
import org.lumatrace.cloud.lumatrace.LumaTraceCanonicalizer;
import org.lumatrace.cloud.lumatrace.LumaTraceManifest;
import org.lumatrace.cloud.model.PhotoRegistration;
import org.lumatrace.cloud.repository.PhotoRepository;
import org.lumatrace.core.KeyDerivation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/photos")
public class RegistrationController {

    private final PhotoRepository repository;

    // En producción, esto vendría de una Variable de Entorno (ej: System.getenv("LUMA_MASTER_KEY"))
    private static final long SERVER_MASTER_KEY = 123456789L;

    public RegistrationController(PhotoRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/register")
    public ResponseEntity<PhotoRegistrationResponse> registerPhoto(
            @RequestBody PhotoRegistrationRequest request
    ) {
        // 1. Persistencia (JPA)
        PhotoRegistration entity = new PhotoRegistration();
        entity.setUserId(request.getUserId());
        entity.setDeviceModel(request.getDeviceModel());
        entity.setLatitude(request.getLatitude());
        entity.setLongitude(request.getLongitude());

        PhotoRegistration saved = repository.save(entity);

        // 2. Lógica C2PA (Canonicalización)
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

        // 3. Integración con el CORE (Derivación de Clave)
        // Usamos el ID generado por la BD + MasterKey para crear la semilla única
        long derivedSeed = KeyDerivation.deriveSeed(
                SERVER_MASTER_KEY,
                saved.getUserId(),
                saved.getId().toString()
        );

        // 4. Respuesta
        PhotoRegistrationResponse response = PhotoRegistrationResponse.builder()
                .photoId(saved.getId())
                .createdAt(saved.getCreatedAt())
                .canonicalJson(canonicalJson)
                .canonicalHash(hash)
                .watermarkSeed(derivedSeed) // <--- Enviamos la semilla al cliente
                .build();

        System.out.printf("[CLOUD] Registered Photo %s for User %s. Seed: %d%n",
                saved.getId(), saved.getUserId(), derivedSeed);

        return ResponseEntity.ok(response);
    }
}
