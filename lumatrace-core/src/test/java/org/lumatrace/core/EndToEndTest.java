//package org.lumatrace.core;
//
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.io.TempDir;
//
//import javax.imageio.ImageIO;
//import java.awt.Color;
//import java.awt.Graphics2D;
//import java.awt.image.BufferedImage;
//import java.io.File;
//import java.nio.file.Path;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class EndToEndTest {
//
//    @Test
//    void testEmbedAndDetect(@TempDir Path tempDir) throws Exception {
//        // 1. Imagen Base: Gris Medio (128)
//        // Usamos gris para estar en el centro del rango dinámico y evitar clipping.
//        BufferedImage source = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
//        Graphics2D g = source.createGraphics();
//        g.setColor(new Color(128, 128, 128));
//        g.fillRect(0, 0, 512, 512);
//
//        // Añadimos un elemento de contraste
//        g.setColor(Color.BLACK);
//        g.fillRect(100, 100, 200, 200);
//        g.dispose();
//
//        long key = 123456789L;
//        String user = "junit-tester";
//        String content = "test-asset";
//
//        WatermarkEngine engine = new WatermarkEngine();
//        WatermarkDetector detector = new WatermarkDetector();
//
//        // 2. Incrustar
//        BufferedImage watermarked = engine.embedWatermark(source, key, user, content);
//
//        // 3. Guardar como PNG (Lossless)
//        // Esto verifica que el algoritmo funciona matemáticamente, sin culpar a la compresión JPEG.
//        File savedFile = tempDir.resolve("test_secured.png").toFile();
//        ImageIO.write(watermarked, "png", savedFile);
//
//        // 4. Detectar
//        var result = detector.detect(ImageIO.read(savedFile), key, user, content);
//
//        System.out.println("EndToEnd Result: " + result);
//
//        // 5. Validar
//        assertTrue(result.detected(), "El detector debe encontrar la marca en PNG (Lossless)");
//        assertTrue(result.confidenceZ() > 10.0, "La confianza debe ser extrema (>10) en condiciones sin pérdida");
//    }
//}