package org.lumatrace.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EndToEndTest {

    @Test
    void testEmbedAndDetect(@TempDir Path tempDir) throws Exception {
        // 1. Crear una imagen sintética en memoria (para no depender de archivos externos)
        BufferedImage source = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        // Pintar algo de ruido para simular una foto real (las fotos negras puras son difíciles)
        for(int x=0; x<512; x++) {
            for(int y=0; y<512; y++) {
                source.setRGB(x, y, (x * y) % 0xFFFFFF);
            }
        }

        // 2. Configuración
        long key = 123456789L;
        String user = "junit-tester";
        String content = "test-asset";
        WatermarkEngine engine = new WatermarkEngine();

        // 3. Incrustar
        BufferedImage watermarked = engine.embedWatermark(source, key, user, content);
        assertNotNull(watermarked, "La imagen resultante no debe ser null");

        // 4. Guardar temporalmente (para simular I/O real)
        File savedFile = tempDir.resolve("test_secured.jpg").toFile();
        ImageIO.write(watermarked, "jpg", savedFile);

        // 5. Detectar
        DetectionReport report = Detector.analyzeImage(savedFile, key, user, content);

        // 6. Validar
        System.out.println("Test Verdict: " + report.getVerdict() + " (Sigma: " + report.getSigma() + ")");

        // Esperamos que pase, o al menos que detecte algo (Sigma > 0)
        assertTrue(report.getSigma() > 3.0, "La señal debería ser detectable inmediatamente después de incrustar");
        assertNotEquals(DetectionVerdict.FAIL, report.getVerdict(), "El veredicto no debería ser FAIL en una imagen limpia");
    }
}