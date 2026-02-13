package org.lumatrace.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

class WatermarkCoreTest {

    @Test
    void testWatermarkLifecycle() {
        System.out.println("TEST: Testing Watermark Injection & Detection Cycle...");

        // 1. Configuración
        int width = 512;
        int height = 512;
        long masterKey = 123456789L;
        String user = "test-user";
        String id = "test-content-id";

        // 2. Generar una imagen de ruido aleatorio (simula una foto real)
        int[] originalPixels = new int[width * height];
        Random rand = new Random();
        for (int i = 0; i < originalPixels.length; i++) {
            int gray = rand.nextInt(255);
            // Formato ARGB: Alpha 255 | R | G | B
            originalPixels[i] = (0xFF << 24) | (gray << 16) | (gray << 8) | gray;
        }

        // 3. INYECCIÓN (Embed)
        WatermarkEngine engine = new WatermarkEngine();
        int[] markedPixels = engine.embedWatermark(originalPixels, width, height, masterKey, user, id);

        assertNotNull(markedPixels, "Marked pixels should not be null");
        assertEquals(originalPixels.length, markedPixels.length, "Pixel count should remain constant");

        // 4. DETECCIÓN (Detect)
        WatermarkDetector detector = new WatermarkDetector();
        DetectionReport report = detector.detect(markedPixels, width, height, masterKey, user, id);

        // 5. ASERCIONES (Validación)
        System.out.println("   -> Sigma Result: " + report.confidenceSigma());

        assertTrue(report.confidenceSigma() > 4.0, "Confidence Sigma should be strong (> 4.0) for a clean image");
        assertTrue(report.estimatedScale() > 0.9 && report.estimatedScale() < 1.1, "Scale should be approx 1.0x");

        System.out.println("TEST PASSED: Core logic is sound.");
    }

    @Test
    void testTamperResistance() {
        System.out.println("TEST: Testing Tamper Resistance (Cropping)...");

        // 1. Setup básico
        int width = 1000;
        int height = 1000;
        long masterKey = 999L;
        int[] pixels = new int[width * height];
        // Rellenar con gris
        for(int i=0; i<pixels.length; i++) pixels[i] = 0xFF888888;

        // 2. Inyectar
        WatermarkEngine engine = new WatermarkEngine();
        int[] marked = engine.embedWatermark(pixels, width, height, masterKey, "u", "i");

        // 3. ATAQUE: Recortar el centro (Crop 50%)
        int cropW = 500;
        int cropH = 500;
        int[] croppedPixels = new int[cropW * cropH];

        int startX = (width - cropW) / 2;
        int startY = (height - cropH) / 2;

        for(int y=0; y<cropH; y++) {
            for(int x=0; x<cropW; x++) {
                int srcIdx = (startY + y) * width + (startX + x);
                int dstIdx = y * cropW + x;
                croppedPixels[dstIdx] = marked[srcIdx];
            }
        }

        // 4. Detectar en la imagen recortada
        WatermarkDetector detector = new WatermarkDetector();
        DetectionReport report = detector.detect(croppedPixels, cropW, cropH, masterKey, "u", "i");

        System.out.println("   -> Cropped Sigma: " + report.confidenceSigma());
        assertTrue(report.confidenceSigma() > 3.0, "Should detect watermark even after 50% cropping");

        System.out.println("TEST PASSED: Algorithm is robust.");
    }
}