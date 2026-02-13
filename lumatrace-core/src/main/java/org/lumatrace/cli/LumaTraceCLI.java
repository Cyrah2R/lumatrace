package org.lumatrace.cli;

import org.lumatrace.core.*;
import javax.imageio.*;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LumaTrace V3 Enterprise CLI
 * Updated to use the new Platform-Agnostic WatermarkEngine & Detector.
 */
public class LumaTraceCLI {

    private static final Logger LOGGER = Logger.getLogger(LumaTraceCLI.class.getName());
    private static final String CONFIG_FILE = "lumatrace.properties";
    private static final String VERSION = "3.1.0-ENTERPRISE";

    private static long MASTER_KEY;
    private static String DEFAULT_USER;
    private static String DEFAULT_CONTENT;
    private static float JPEG_QUALITY;
    private static boolean VERBOSE;

    static {
        initializeConfig();
        setupLogging();
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                printUsage();
                System.exit(1);
            }
            int exitCode = switch (args[0].toLowerCase()) {
                case "embed", "-e" -> processEmbed(args);
                case "detect", "-d" -> processDetect(args);
                case "batch" -> processBatch(args);
                case "keygen", "-g" -> generateKey();
                case "version", "-v" -> { showVersion(); yield 0; }
                default -> { printUsage(); yield 1; }
            };
            System.exit(exitCode);
        } catch (Exception e) {
            System.err.println("[FATAL] " + e.getMessage());
            if (VERBOSE) e.printStackTrace();
            System.exit(3);
        }
    }

    private static int processEmbed(String[] args) throws Exception {
        validateArgs(args, 2, "embed <input> [output]");
        File input = validateFile(args[1], true);
        File output = args.length > 2 ? validateFile(args[2], false) : generateOutputFile(input);

        BufferedImage src = ImageIO.read(input);
        if (src == null) throw new IllegalArgumentException("Unsupported image format");

        WatermarkEngine engine = new WatermarkEngine();
        long start = System.nanoTime();

        // --- ADAPTACIÓN (AWT -> Raw Int) ---
        int w = src.getWidth();
        int h = src.getHeight();
        int[] rawPixels = src.getRGB(0, 0, w, h, null, 0, w);

        // Procesar (Pure Math)
        int[] processedPixels = engine.embedWatermark(rawPixels, w, h, MASTER_KEY, DEFAULT_USER, DEFAULT_CONTENT);

        // Reconstruir (Raw Int -> AWT)
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        result.setRGB(0, 0, w, h, processedPixels, 0, w);
        // ------------------------------------

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        saveJpeg(result, output, JPEG_QUALITY);
        printEmbedTelemetry(input, output, durationMs, src.getWidth(), src.getHeight());
        return 0;
    }

    private static int processDetect(String[] args) throws Exception {
        validateArgs(args, 2, "detect <input>");

        File input = validateFile(args[1], true);
        BufferedImage img = null;
        try {
            img = ImageIO.read(input);
            if (img == null) throw new IllegalArgumentException("IO_ERROR: Decoding failed");

            WatermarkDetector detector = new WatermarkDetector();
            long start = System.nanoTime();

            // --- ADAPTACIÓN (AWT -> Raw Int) ---
            int w = img.getWidth();
            int h = img.getHeight();
            int[] rawPixels = img.getRGB(0, 0, w, h, null, 0, w);

            // Llamada al detector puro (Devuelve DetectionReport)
            DetectionReport report = detector.detect(rawPixels, w, h, MASTER_KEY, DEFAULT_USER, DEFAULT_CONTENT);
            // -----------------------------------

            long durationMs = (System.nanoTime() - start) / 1_000_000;

            printDetectionReport(report, input, durationMs);

            // Usamos Sigma > 4.0 como criterio de éxito
            return (report.confidenceSigma() > 4.0) ? 0 : 4;

        } finally {
            if (img != null) img.flush();
        }
    }

    private static int processBatch(String[] args) {
        validateArgs(args, 2, "batch <input-dir> [output-dir]");
        File inDir = validateDirectory(args[1]);
        File outDir = args.length > 2 ? new File(args[2]) : new File(inDir, "output_protected");
        if (!outDir.exists() && !outDir.mkdirs()) return 1;

        File[] files = inDir.listFiles((d, n) -> n.toLowerCase().matches(".*\\.(jpg|jpeg|png)$"));
        if (files == null || files.length == 0) return 1;

        System.out.printf("TELEMETRY: Initializing batch for %d units...\n", files.length);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (File file : files) {
                executor.submit(() -> {
                    try {
                        BufferedImage src = ImageIO.read(file);
                        if (src != null) {
                            WatermarkEngine engine = new WatermarkEngine();
                            int w = src.getWidth();
                            int h = src.getHeight();

                            int[] raw = src.getRGB(0, 0, w, h, null, 0, w);
                            int[] outPixels = engine.embedWatermark(raw, w, h, MASTER_KEY, DEFAULT_USER, DEFAULT_CONTENT);

                            BufferedImage res = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                            res.setRGB(0, 0, w, h, outPixels, 0, w);

                            saveJpeg(res, new File(outDir, "PROT_" + file.getName()), JPEG_QUALITY);
                            System.out.println("UNIT_COMPLETE: " + file.getName());
                        }
                    } catch (Exception e) {
                        System.err.println("UNIT_FAILED: " + file.getName());
                    }
                });
            }
        }
        return 0;
    }

    // --- Helpers Utilitarios ---

    private static void saveJpeg(BufferedImage img, File file, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IllegalStateException("RESOURCE_ERROR: JPEG Encoder unavailable");

        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            try (FileImageOutputStream out = new FileImageOutputStream(file)) {
                writer.setOutput(out);
                writer.write(null, new IIOImage(img, null, null), param);
            }
        } finally {
            writer.dispose();
        }
    }

    private static int generateKey() {
        SecureRandom rnd = new SecureRandom();
        long key = rnd.nextLong();
        System.out.println("KEY: " + key);
        return 0;
    }

    private static void initializeConfig() {
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(new File(CONFIG_FILE))) { props.load(is); } catch (Exception ignored) {}

        String envKey = System.getenv("LUMATRACE_MASTER_KEY");
        String keyStr = envKey != null ? envKey : props.getProperty("master.key", "123456789");

        try {
            MASTER_KEY = Long.parseLong(keyStr);
        } catch (NumberFormatException e) {
            LOGGER.warning("INVALID KEY FORMAT. Using Default.");
            MASTER_KEY = 123456789L;
        }

        DEFAULT_USER = props.getProperty("default.user", "system-auth");
        DEFAULT_CONTENT = props.getProperty("default.content", "payload-v1");
        JPEG_QUALITY = Float.parseFloat(props.getProperty("jpeg.quality", "0.95"));
        VERBOSE = Boolean.parseBoolean(props.getProperty("verbose", "false"));
    }

    private static void setupLogging() { if (!VERBOSE) LOGGER.setLevel(Level.SEVERE); }
    private static void printUsage() { System.out.println("LumaTrace Enterprise CLI v" + VERSION + "\nUsage: embed, detect, batch, keygen"); }
    private static File validateFile(String p, boolean e) { File f = new File(p); if(e && !f.exists()) throw new IllegalArgumentException("File not found: " + p); return f; }
    private static File validateDirectory(String p) { File f = new File(p); if(!f.isDirectory()) throw new IllegalArgumentException("Not a dir: " + p); return f; }
    private static File generateOutputFile(File i) { return new File(i.getParent(), "PROT_" + i.getName()); }
    private static void printEmbedTelemetry(File i, File o, long ms, int w, int h) { System.out.printf("[METRICS] %s -> %s (%d ms)\n", i.getName(), o.getName(), ms); }
    private static void showVersion() { System.out.println(VERSION); }
    private static void validateArgs(String[] a, int l, String u) { if(a.length < l) throw new IllegalArgumentException(u); }

    // CORREGIDO: Usamos DetectionReport
    private static void printDetectionReport(DetectionReport report, File in, long ms) {
        boolean detected = report.confidenceSigma() > 4.0;
        System.out.println("\n--- FORENSIC_ANALYSIS_REPORT ---");
        System.out.printf("Target:     %s\n", in.getName());
        System.out.printf("Signal:     %s\n", detected ? "VALIDATED" : "NOT_DETECTED");
        System.out.printf("Z_Score:    σ = %.4f\n", report.confidenceSigma());
        System.out.printf("Scale_Est:  %.2fx\n", report.estimatedScale());
        System.out.printf("Analysis:   %d ms\n", ms);
        System.out.println("--------------------------------");
    }
}