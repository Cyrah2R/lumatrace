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
 * Forensic Robustness Validation Suite.
 * Automated evaluation of signal resilience against common signal processing
 * Attack Vectors: DCT compression, spatial interpolation, and geometric clipping.
 * UPDATED: Uses raw pixel arrays to communicate with the Platform-Agnostic Core.
 */
public class RobustnessBenchmark {

    private static final String BENCHMARK_SESSION_UID = "internal-audit-v3";
    private static final String TEST_PAYLOAD = "robustness-validation-vector";
    private static final double SIGMA_CONFIDENCE_THRESHOLD = 4.0;

    /**
     * Executes a comprehensive robustness audit on a target asset.
     * @param sourceFile The master asset for signal injection.
     * @param masterKey  The 64-bit root security key.
     */
    public void runSuite(File sourceFile, long masterKey) {
        try {
            System.out.println("[IO_INIT] Accessing master asset: " + sourceFile.getName());
            BufferedImage original = ImageIO.read(sourceFile);
            if (original == null) throw new IOException("Signal decode failure");

            WatermarkEngine engine = new WatermarkEngine();
            System.out.println("[PROC_EMBED] Generating reference watermarked asset...");

            // --- ADAPTACIÓN (BufferedImage -> int[]) ---
            int w = original.getWidth();
            int h = original.getHeight();
            int[] rawPixels = original.getRGB(0, 0, w, h, null, 0, w);

            // Inyección (Matemática Pura)
            int[] markedPixels = engine.embedWatermark(rawPixels, w, h, masterKey, BENCHMARK_SESSION_UID, TEST_PAYLOAD);

            // Reconstrucción (int[] -> BufferedImage)
            BufferedImage watermarked = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            watermarked.setRGB(0, 0, w, h, markedPixels, 0, w);
            // -------------------------------------------

            System.out.println("\n--- FORENSIC ROBUSTNESS AUDIT REPORT ---");
            System.out.printf("%-20s | %-12s | %-12s | %-10s%n", "VECTOR", "PARAMETERS", "STAT_SIGMA", "VERDICT");
            System.out.println("------------------------------------------------------------------");

            WatermarkDetector detector = new WatermarkDetector();

            // Attack Vector A: Discrete Cosine Transform (JPEG Degradation)
            runDctAttack(watermarked, masterKey, 0.90f, detector);
            runDctAttack(watermarked, masterKey, 0.70f, detector);
            runDctAttack(watermarked, masterKey, 0.50f, detector);

            // Attack Vector B: Spatial Resampling (Interpolation)
            runResamplingAttack(watermarked, masterKey, 0.75, detector);
            runResamplingAttack(watermarked, masterKey, 0.50, detector);

            // Attack Vector C: Geometric Clipping (Center Crop)
            runClippingAttack(watermarked, masterKey, 0.80, detector);
            runClippingAttack(watermarked, masterKey, 0.50, detector);

            System.out.println("------------------------------------------------------------------");
            System.out.println("[AUDIT_COMPLETE] Session state: Terminated.\n");

        } catch (Exception e) {
            System.err.println("[CRITICAL_BENCHMARK_FAILURE] Audit aborted: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void runDctAttack(BufferedImage img, long key, float quality, WatermarkDetector detector) {
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
            evaluateVector("DCT_COMPRESSION", String.format("Q=%.2f", quality), attacked, key, detector);

        } catch (IOException e) {
            logVectorError("DCT_COMPRESSION", "IO_CODEC_FAILURE");
        }
    }

    private void runResamplingAttack(BufferedImage img, long key, double scale, WatermarkDetector detector) {
        int w = (int) (img.getWidth() * scale);
        int h = (int) (img.getHeight() * scale);
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();

        evaluateVector("RESAMPLING", String.format("S=%.2fx", scale), scaled, key, detector);
    }

    private void runClippingAttack(BufferedImage img, long key, double ratio, WatermarkDetector detector) {
        int w = img.getWidth();
        int h = img.getHeight();
        int newW = (int) (w * ratio);
        int newH = (int) (h * ratio);
        BufferedImage cropped = img.getSubimage((w - newW) / 2, (h - newH) / 2, newW, newH);

        evaluateVector("GEO_CLIPPING", String.format("R=%.2f", ratio), cropped, key, detector);
    }

    private void evaluateVector(String vector, String param, BufferedImage img, long key, WatermarkDetector detector) {
        // --- ADAPTACIÓN (BufferedImage -> int[]) ---
        int w = img.getWidth();
        int h = img.getHeight();
        int[] rawPixels = img.getRGB(0, 0, w, h, null, 0, w);

        DetectionReport report = detector.detect(rawPixels, w, h, key, BENCHMARK_SESSION_UID, TEST_PAYLOAD);

        // Obtenemos Sigma del reporte
        double sigma = report.confidenceSigma();

        // Status determination based on empirical Sigma thresholds
        String verdict = (sigma >= SIGMA_CONFIDENCE_THRESHOLD) ? "VERIFIED" : (sigma > 2.5 ? "MARGINAL" : "FAILED");

        System.out.printf("%-20s | %-12s | %-12.4f | %-10s%n", vector, param, sigma, verdict);
    }

    private void logVectorError(String vector, String msg) {
        System.out.printf("%-20s | %-12s | %-12s | %-10s%n", vector, "N/A", "ERR_CODE", msg);
    }
}