package org.lumatrace.core;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * Integration test suite designed to evaluate the watermark's survivability
 * against common adversarial attacks (Compression, Scaling, Cropping).
 */
public class RobustnessBenchmark {

    private static final Logger logger = Logger.getLogger(RobustnessBenchmark.class.getName());

    private static final String USER_ID = "benchmark-user";
    private static final String CONTENT_ID = "benchmark-content-v1";
    private static final long MASTER_KEY = 0xDEADBEEFL; // Hex literal implies system testing

    public static void main(String[] args) {
        try {
            new RobustnessBenchmark().runSuite();
        } catch (Exception e) {
            logger.severe("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void runSuite() throws Exception {
        File originalFile = new File("original.jpg");
        if (!originalFile.exists()) {
            throw new IllegalArgumentException("Missing fixture: original.jpg");
        }

        printHeader();

        WatermarkEngine engine = new WatermarkEngine();
        BufferedImage original = ImageIO.read(originalFile);

        // 1. Baseline Embedding
        System.out.println("[INFO] Phase 1: Embedding Watermark...");
        BufferedImage marked = engine.embedWatermark(original, MASTER_KEY, USER_ID, CONTENT_ID);
        saveJpg(marked, new File("bench_marked.jpg"), 0.95f);
        verify("Baseline (No Attack)", "bench_marked.jpg");

        // 2. Compression Attacks
        System.out.println("[INFO] Phase 2: Simulating Compression Attacks...");
        saveJpg(marked, new File("bench_jpeg70.jpg"), 0.70f);
        saveJpg(marked, new File("bench_jpeg50.jpg"), 0.50f);

        verify("JPEG Quality 70", "bench_jpeg70.jpg");
        verify("JPEG Quality 50", "bench_jpeg50.jpg");

        // 3. Geometric Attacks
        System.out.println("[INFO] Phase 3: Simulating Geometric Attacks...");

        // Resize 50%
        BufferedImage resized = resize(marked, marked.getWidth() / 2, marked.getHeight() / 2);
        saveJpg(resized, new File("bench_resize.jpg"), 0.85f);
        verify("Scaling 50%", "bench_resize.jpg");

        // Crop Center 80%
        int w = marked.getWidth();
        int h = marked.getHeight();
        BufferedImage crop = marked.getSubimage(w / 10, h / 10, w * 8 / 10, h * 8 / 10);
        saveJpg(crop, new File("bench_crop.jpg"), 0.85f);
        verify("Cropping 80%", "bench_crop.jpg");

        System.out.println("---------------------------------------------------------------");
        System.out.println("[INFO] Benchmark Suite Completed.");
    }

    private void verify(String scenario, String filename) {
        try {
            DetectionReport report = Detector.analyzeImage(new File(filename), MASTER_KEY, USER_ID, CONTENT_ID);

            // Format aligned for readability
            System.out.printf("   %-20s | Sigma: %6.2f | Scale: %4.2f | Verdict: %s%n",
                    scenario,
                    report.getSigma(),
                    report.getScale(),
                    report.getVerdict());

        } catch (Exception e) {
            System.err.printf("   %-20s | ERROR: %s%n", scenario, e.getMessage());
        }
    }

    private void saveJpg(BufferedImage img, File file, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IllegalStateException("No JPEG writer found");

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        try (FileImageOutputStream out = new FileImageOutputStream(file)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(img, null, null), param);
        }
        writer.dispose();
    }

    private BufferedImage resize(BufferedImage img, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private void printHeader() {
        System.out.println("===============================================================");
        System.out.println(" LumaTrace Robustness Benchmark v4.0");
        System.out.println("===============================================================");
        System.out.printf(" %-20s | %-12s | %-10s | %-10s%n", "SCENARIO", "SIGMA (Z)", "SCALE", "VERDICT");
        System.out.println("---------------------------------------------------------------");
    }
}