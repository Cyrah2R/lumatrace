package org.lumatrace.core;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * In-Memory Robustness Benchmark.
 * Validates resilience against JPEG compression, Scaling, and Cropping
 * without disk I/O overhead.
 */
public class RobustnessBenchmark {

    private static final String DEFAULT_USER = "benchmark-user";
    private static final String DEFAULT_CONTENT = "benchmark-content";

    public void runSuite(File sourceFile, long masterKey) {
        try {
            System.out.println("Loading source image...");
            BufferedImage original = ImageIO.read(sourceFile);
            if (original == null) throw new IOException("Cannot read image");

            // 1. Embed Reference
            WatermarkEngine engine = new WatermarkEngine();
            System.out.println("Embedding reference watermark...");
            BufferedImage watermarked = engine.embedWatermark(original, masterKey, DEFAULT_USER, DEFAULT_CONTENT);

            System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║                ROBUSTNESS BENCHMARK RESULTS                  ║");
            System.out.println("╠══════════════════════════════════════════════════════════════╣");
            System.out.printf("║ %-25s │ %-10s │ %-8s │ %-8s ║%n", "ATTACK TYPE", "PARAM", "SIGMA", "VERDICT");
            System.out.println("╠══════════════════════════════════════════════════════════════╣");

            // Instantiate detector once
            WatermarkDetector detector = new WatermarkDetector();

            // Run Tests
            testJpeg(watermarked, masterKey, 0.9f, detector);
            testJpeg(watermarked, masterKey, 0.7f, detector);
            testJpeg(watermarked, masterKey, 0.5f, detector);

            System.out.println("╠══════════════════════════════════════════════════════════════╣");

            testScale(watermarked, masterKey, 0.75, detector);
            testScale(watermarked, masterKey, 0.50, detector);

            System.out.println("╠══════════════════════════════════════════════════════════════╣");

            testCrop(watermarked, masterKey, 0.80, detector);
            testCrop(watermarked, masterKey, 0.50, detector);

            System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        } catch (Exception e) {
            System.err.println("Benchmark critical failure: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void testJpeg(BufferedImage img, long key, float quality, WatermarkDetector detector) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
                writer.write(null, new IIOImage(img, null, null), param);
            }
            writer.dispose();

            BufferedImage attacked = ImageIO.read(new ByteArrayInputStream(baos.toByteArray()));
            runDetection("JPEG Compression", String.format("Q=%.0f%%", quality * 100), attacked, key, detector);

        } catch (IOException e) {
            printError("JPEG", "Error");
        }
    }

    private void testScale(BufferedImage img, long key, double scale, WatermarkDetector detector) {
        int w = (int) (img.getWidth() * scale);
        int h = (int) (img.getHeight() * scale);
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();

        runDetection("Rescaling", String.format("%.0f%%", scale * 100), scaled, key, detector);
    }

    private void testCrop(BufferedImage img, long key, double keepRatio, WatermarkDetector detector) {
        int w = img.getWidth();
        int h = img.getHeight();
        int newW = (int) (w * keepRatio);
        int newH = (int) (h * keepRatio);
        BufferedImage cropped = img.getSubimage((w - newW) / 2, (h - newH) / 2, newW, newH);

        runDetection("Center Crop", String.format("Keep %.0f%%", keepRatio * 100), cropped, key, detector);
    }

    private void runDetection(String attack, String param, BufferedImage img, long key, WatermarkDetector detector) {
        // Adapt V3 Detector Result to Reporting DTO
        WatermarkDetector.DetectionResult v3Result = detector.detect(img, key, DEFAULT_USER, DEFAULT_CONTENT);

        // Map v3 result (record) to benchmark display logic
        double sigma = v3Result.confidenceZ();
        String icon = v3Result.detected() ? "✅ PASS" : (sigma > 2.5 ? "⚠️ WEAK" : "❌ FAIL");

        System.out.printf("║ %-25s │ %-10s │ %-8.4f │ %-8s ║%n", attack, param, sigma, icon);
    }

    private void printError(String name, String param) {
        System.out.printf("║ %-25s │ %-10s │ %-8s │ %-8s ║%n", name, param, "ERR", "ERROR");
    }
}