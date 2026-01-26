package org.lumatrace.cli;

import org.lumatrace.core.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Command Line Interface entry point for the LumaTrace Watermarking Utility.
 */
public class LumaTraceCLI {

    // In a real production env, these would come from Environment Variables or a Vault.
    private static final long MASTER_KEY_DEMO = 123456789L;
    private static final String DEFAULT_USER = "demo-user";
    private static final String DEFAULT_CONTENT = "demo-content";

    public static void main(String[] args) {
        printBanner();

        if (args.length == 0) {
            printHelp();
            System.exit(1);
        }

        String mode = args[0];
        try {
            switch (mode) {
                case "-e": // Embed
                case "--embed":
                    runEmbed(args);
                    break;
                case "-d": // Detect
                case "--detect":
                    runDetect(args);
                    break;
                case "-h":
                case "--help":
                default:
                    printHelp();
            }
        } catch (Exception e) {
            System.err.println("\n[ERROR] CRITICAL ERROR: " + e.getMessage());
            e.printStackTrace(); // Keep stacktrace for debugging in beta
            System.exit(2);
        }
    }

    private static void runEmbed(String[] args) throws Exception {
        String inputFile = args.length > 1 ? args[1] : "original.jpg";
        String outputFile = args.length > 2 ? args[2] : "secured.jpg";

        System.out.println("[INFO] Processing: " + inputFile);
        File file = new File(inputFile);
        if (!file.exists()) throw new IllegalArgumentException("Input file not found: " + inputFile);

        BufferedImage img = ImageIO.read(file);
        WatermarkEngine engine = new WatermarkEngine();

        long start = System.currentTimeMillis();
        BufferedImage out = engine.embedWatermark(img, MASTER_KEY_DEMO, DEFAULT_USER, DEFAULT_CONTENT);
        long duration = System.currentTimeMillis() - start;

        ImageIO.write(out, "jpg", new File(outputFile));

        // CAMBIO AQUÍ: Usamos [SUCCESS] en vez de ✔ para evitar problemas de encoding
        System.out.printf("[SUCCESS] SECURED in %dms. Output saved to: %s%n", duration, outputFile);
    }

    private static void runDetect(String[] args) throws Exception {
        String inputFile = args.length > 1 ? args[1] : "secured.jpg";

        File file = new File(inputFile);
        if (!file.exists()) throw new IllegalArgumentException("Input file not found: " + inputFile);

        System.out.println("[INFO] Analyzing signal in: " + inputFile);
        DetectionReport report = Detector.analyzeImage(file, MASTER_KEY_DEMO, DEFAULT_USER, DEFAULT_CONTENT);

        System.out.println("\n------------------------------------------------");
        System.out.printf(" ANALYSIS REPORT | Duration: %dms%n", report.getExecutionTimeMs());
        System.out.println("------------------------------------------------");
        System.out.printf(" Signal Strength (Sigma) : %.4f%n", report.getSigma());
        System.out.printf(" Estimated Scale         : %.2fx%n", report.getScale());
        System.out.printf(" FINAL VERDICT           : %s%n", report.getVerdict());
        System.out.println("------------------------------------------------");
    }

    private static void printBanner() {
        System.out.println("################################################");
        System.out.println("#           LUMATRACE C2PA UTILITY             #");
        System.out.println("#      Robust Soft-Binding Reference Impl      #");
        System.out.println("#            (c) 2026 es.lumatrace             #");
        System.out.println("################################################");
    }

    private static void printHelp() {
        System.out.println("Usage:");
        // He actualizado el nombre del JAR para que coincida con el que genera Maven
        System.out.println("  java -jar lumatrace-core-1.0.0.jar -e [input.jpg] [output.jpg]");
        System.out.println("  java -jar lumatrace-core-1.0.0.jar -d [input.jpg]");
    }
}