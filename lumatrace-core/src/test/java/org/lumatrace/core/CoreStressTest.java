//package org.lumatrace.core;
//
//import org.junit.jupiter.api.Test;
//import java.awt.Color;
//import java.awt.Graphics2D;
//import java.awt.RenderingHints;
//import java.awt.image.BufferedImage;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.stream.IntStream;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class CoreStressTest {
//
//    private final WatermarkEngine engine = new WatermarkEngine();
//    private final WatermarkDetector detector = new WatermarkDetector();
//
//    @Test
//    void testValidationEdgeCases() {
//        assertThrows(IllegalArgumentException.class, () -> engine.embedWatermark(null, 123L, "u", "c"));
//        assertThrows(IllegalArgumentException.class, () -> detector.detect(null, 123L, "u", "c"));
//    }
//
//    @Test
//    void testFalsePositives() {
//        BufferedImage cleanImage = createSafeImage(512, 512);
//        var resultClean = detector.detect(cleanImage, 12345L, "u", "c");
//        assertFalse(resultClean.detected(), "Falso positivo en imagen limpia");
//    }
//
//    @Test
//    void testRobustnessScaling() {
//        BufferedImage original = createSafeImage(1024, 1024);
//        long key = 987654321L;
//        BufferedImage secured = engine.embedWatermark(original, key, "u", "c");
//
//        // Escalado 75% con interpolación Bilineal (estándar)
//        double scale = 0.75;
//        int newW = (int)(original.getWidth() * scale);
//        int newH = (int)(original.getHeight() * scale);
//
//        BufferedImage shrunk = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
//        Graphics2D g = shrunk.createGraphics();
//        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
//        g.drawImage(secured, 0, 0, newW, newH, null);
//        g.dispose();
//
//        var result = detector.detect(shrunk, key, "u", "c");
//        System.out.printf("Scaling Result: %s%n", result);
//
//        assertTrue(result.detected(), "Debe sobrevivir al escalado del 75%");
//        assertEquals(scale, result.detectedScale(), 0.1);
//    }
//
//    @Test
//    void testConcurrencySafety() {
//        // Reducido a 10 hilos para no saturar entornos CI pequeños, suficiente para probar thread-safety
//        int threads = 10;
//        BufferedImage masterImage = createSafeImage(512, 512);
//        AtomicInteger successCount = new AtomicInteger(0);
//
//        IntStream.range(0, threads).parallel().forEach(i -> {
//            try {
//                long key = i + 50000;
//                // Usamos nuevas instancias para garantizar aislamiento total
//                WatermarkEngine localEngine = new WatermarkEngine();
//                WatermarkDetector localDetector = new WatermarkDetector();
//
//                BufferedImage w = localEngine.embedWatermark(masterImage, key, "u", "c");
//                var res = localDetector.detect(w, key, "u", "c");
//
//                if (res.detected()) successCount.incrementAndGet();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        });
//
//        assertEquals(threads, successCount.get(), "Fallo en concurrencia: No todos los hilos completaron");
//    }
//
//    // Helper: Imagen Gris Medio (Segura para evitar clipping)
//    private BufferedImage createSafeImage(int w, int h) {
//        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
//        Graphics2D g = img.createGraphics();
//
//        // Gris base 128. Esto permite sumar/restar la marca sin que se corte en 0 o 255.
//        g.setColor(new Color(128, 128, 128));
//        g.fillRect(0, 0, w, h);
//
//        // Un poco de variación suave
//        g.setColor(new Color(100, 100, 100));
//        g.fillRect(w/4, h/4, w/2, h/2);
//
//        g.dispose();
//        return img;
//    }
//}