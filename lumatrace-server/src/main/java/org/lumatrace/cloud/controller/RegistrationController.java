package org.lumatrace.cloud.controller;

import jakarta.validation.Valid;
import org.lumatrace.cloud.dto.PhotoRegistrationRequest;
import org.lumatrace.cloud.dto.PhotoRegistrationResponse;
import org.lumatrace.cloud.dto.VerificationResponse;
import org.lumatrace.cloud.lumatrace.LumaTraceCanonicalizer;
import org.lumatrace.cloud.lumatrace.LumaTraceManifest;
import org.lumatrace.cloud.model.PhotoRegistration;
import org.lumatrace.cloud.repository.PhotoRepository;
import org.lumatrace.core.KeyDerivation;
import org.lumatrace.core.WatermarkDetector;
import org.lumatrace.core.DetectionReport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

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

    // --- ENDPOINT 1: REGISTRO (Firma) ---
    @PostMapping("/register")
    public ResponseEntity<PhotoRegistrationResponse> registerPhoto(
            @Valid @RequestBody PhotoRegistrationRequest request
    ) {
        PhotoRegistration saved = repository.save(
                new PhotoRegistration(
                        request.getUserId(),
                        request.getContentHash(),
                        request.getDeviceModel(),
                        request.getLatitude(),
                        request.getLongitude()
                )
        );

        LumaTraceManifest manifest = new LumaTraceManifest(
                "lumatrace/1.0",
                saved.getId(),
                saved.getUserId(),
                saved.getContentHash(),
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

    // --- ENDPOINT 2: VERIFICACIÓN (Detección) ---
    @PostMapping("/verify")
    public ResponseEntity<VerificationResponse> verifyPhoto(
            @RequestParam("image") MultipartFile imageFile,
            @RequestParam("userId") String userId,
            @RequestParam("photoId") String photoId
    ) {
        try {
            if (imageFile.isEmpty()) {
                return ResponseEntity.badRequest().body(new VerificationResponse(false, 0.0, "No image provided"));
            }

            BufferedImage bufferedImage = ImageIO.read(imageFile.getInputStream());
            if (bufferedImage == null) {
                return ResponseEntity.badRequest().body(new VerificationResponse(false, 0.0, "Invalid image format"));
            }

            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();
            int[] pixels = bufferedImage.getRGB(0, 0, width, height, null, 0, width);

            WatermarkDetector detector = new WatermarkDetector();

            // 1. Ejecutar detección
            DetectionReport report = detector.detect(pixels, width, height, masterKey, userId, photoId);

            // 2. Leer los datos CORRECTOS del Record
            double sigma = report.confidenceSigma(); // Nombre correcto
            double scale = report.estimatedScale();  // Nombre correcto

            // Definimos autenticidad: Si sigma >= 4.0 (umbral estándar)
            boolean authentic = sigma >= 4.0;

            String message = String.format("Status: %s | Confidence: %.2f | Scale: %.2fx | Latency: %dms",
                    report.status(), // Enum status
                    sigma,
                    scale,
                    report.latencyMs());

            return ResponseEntity.ok(new VerificationResponse(
                    authentic,
                    sigma,
                    authentic ? "VERIFIED. " + message : "FAILED. " + message
            ));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(new VerificationResponse(false, 0.0, "Error processing image: " + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(new VerificationResponse(false, 0.0, "Internal verification error."));
        }
    }
}