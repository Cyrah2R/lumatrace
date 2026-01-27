package org.lumatrace.cloud.controller;

import jakarta.validation.Valid;
import org.lumatrace.cloud.dto.PhotoRegistrationRequest;
import org.lumatrace.cloud.dto.PhotoRegistrationResponse;
import org.lumatrace.cloud.lumatrace.LumaTraceCanonicalizer;
import org.lumatrace.cloud.lumatrace.LumaTraceManifest;
import org.lumatrace.cloud.model.PhotoRegistration;
import org.lumatrace.cloud.repository.PhotoRepository;
import org.lumatrace.core.KeyDerivation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/photos")
public class RegistrationController {

    private final PhotoRepository repository;
    private final long masterKey;

    public RegistrationController(
            PhotoRepository repository,
            @Value("${lumatrace.master-key}") long masterKey
    ) {
        this.repository = repository;
        this.masterKey = masterKey;
    }

    @PostMapping("/register")
    public ResponseEntity<PhotoRegistrationResponse> registerPhoto(
            @Valid @RequestBody PhotoRegistrationRequest request
    ) {
        PhotoRegistration saved = repository.save(
                new PhotoRegistration(
                        request.getUserId(),
                        request.getDeviceModel(),
                        request.getLatitude(),
                        request.getLongitude()
                )
        );

        LumaTraceManifest manifest = new LumaTraceManifest(
                "lumatrace/1.0",
                saved.getId(),
                saved.getUserId(),
                saved.getDeviceModel(),
                saved.getCreatedAt(),
                saved.getLatitude(),
                saved.getLongitude()
        );

        String canonicalJson = LumaTraceCanonicalizer.toCanonicalJson(manifest);
        String hash = LumaTraceCanonicalizer.sha256(canonicalJson);

        long seed = KeyDerivation.deriveSeed(
                masterKey,
                saved.getUserId(),
                saved.getId().toString()
        );

        return ResponseEntity.ok(
                new PhotoRegistrationResponse(
                        saved.getId(),
                        saved.getCreatedAt(),
                        canonicalJson,
                        hash,
                        seed
                )
        );
    }
}
