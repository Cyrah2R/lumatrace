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
 * Reference implementation for automated content integrity and forensic watermarking.
 */
public class LumaTraceCLI {

    private static final Logger LOGGER = Logger.getLogger(LumaTraceCLI.class.getName());
    private static final String CONFIG_FILE = "lumatrace.properties";
    private static final String VERSION = "3.0.0-RELEASE";

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
                case "benchmark", "-b" -> processBenchmark(args);
                case "batch" -> processBatch(args);
                case "keygen", "-g" -> generateKey();
                case "version", "-v" -> { showVersion(); yield 0; }
                default -> { printUsage(); yield 1; }
            };

            System.exit(exitCode);

        } catch (IllegalArgumentException e) {
            System.err.println("[CLI_ERROR] Parameter validation failed: " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println("[FATAL_EXCEPTION] Runtime execution failure: " + e.getMessage());
            if (VERBOSE) e.printStackTrace();
            System.exit(3);
        }
    }

    private static int processEmbed(String[] args) throws Exception {
        validateArgs(args, 2, "embed <input> [output]");

        File input = validateFile(args[1], true);
        File output = args.length > 2
                ? validateFile(args[2], false)
                : generateOutputFile(input);

        BufferedImage src = null;
        try {
            src = ImageIO.read(input);
            if (src == null) throw new IllegalArgumentException("Codec failure: Unsupported image format");

            WatermarkEngine engine = new WatermarkEngine();
            long start = System.nanoTime();
            BufferedImage result = engine.embedWatermark(src, MASTER_KEY, DEFAULT_USER, DEFAULT_CONTENT);
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            saveJpeg(result, output, JPEG_QUALITY);

            printEmbedTelemetry(input, output, durationMs, src.getWidth(), src.getHeight());
            return 0;

        } finally {
            if (src != null) src.flush();
        }
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
            WatermarkDetector.DetectionResult result = detector.detect(img, MASTER_KEY, DEFAULT_USER, DEFAULT_CONTENT);
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            printDetectionReport(result, input, durationMs);
            return result.detected() ? 0 : 4;

        } finally {
            if (img != null) img.flush();
        }
    }

    private static int processBenchmark(String[] args) {
        String path = args.length > 1 ? args[1] : "test.jpg";
        File file = validateFile(path, true);

        try {
            new RobustnessBenchmark().runSuite(file, MASTER_KEY);
            return 0;
        } catch (Exception e) {
            System.err.println("[BENCHMARK_FATAL] Validation suite failed: " + e.getMessage());
            return 3;
        }
    }

    private static int processBatch(String[] args) {
        validateArgs(args, 2, "batch <input-dir> [output-dir]");

        File inDir = validateDirectory(args[1]);
        File outDir = args.length > 2 ? new File(args[2]) : new File(inDir, "output_protected");

        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalArgumentException("PERM_ERROR: Cannot initialize output directory: " + outDir);
        }

        File[] files = inDir.listFiles((d, n) -> n.toLowerCase().matches(".*\\.(jpg|jpeg|png)$"));
        if (files == null || files.length == 0) return 1;

        System.out.printf("TELEMETRY: Initializing parallel batch for %d units...\n", files.length);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (File file : files) {
                executor.submit(() -> {
                    try {
                        BufferedImage src = ImageIO.read(file);
                        if (src != null) {
                            WatermarkEngine engine = new WatermarkEngine();
                            BufferedImage res = engine.embedWatermark(src, MASTER_KEY, DEFAULT_USER, DEFAULT_CONTENT);
                            saveJpeg(res, new File(outDir, "PROT_" + file.getName()), JPEG_QUALITY);
                            System.out.println("UNIT_COMPLETE: " + file.getName());
                            src.flush();
                        }
                    } catch (Exception e) {
                        System.err.println("UNIT_FAILED: " + file.getName() + " -> " + e.getMessage());
                    }
                });
            }
        }

        return 0;
    }

    private static int generateKey() {
        SecureRandom rnd = new SecureRandom();
        long key = rnd.nextLong();
        System.out.println("--- KEY_GENERATION_REPORT ---");
        System.out.printf("Timestamp: %d\n", System.currentTimeMillis());
        System.out.printf("Decimal:   %d\n", key);
        System.out.printf("Hex:       0x%X\n", key);
        System.out.println("-----------------------------");
        return 0;
    }

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

    private static void initializeConfig() {
        Properties props = new Properties();
        File configFile = new File(CONFIG_FILE);

        if (configFile.exists()) {
            try (InputStream is = new FileInputStream(configFile)) {
                props.load(is);
            } catch (Exception ignored) {}
        }

        String envKey = System.getenv("LUMATRACE_MASTER_KEY");
        if (envKey != null) props.setProperty("master.key", envKey);

        String keyStr = props.getProperty("master.key", "123456789");
        MASTER_KEY = keyStr.startsWith("0x") ? Long.parseUnsignedLong(keyStr.substring(2), 16) : Long.parseLong(keyStr);
        DEFAULT_USER = props.getProperty("default.user", "system-auth");
        DEFAULT_CONTENT = props.getProperty("default.content", "payload-v1");
        JPEG_QUALITY = Float.parseFloat(props.getProperty("jpeg.quality", "0.95"));
        VERBOSE = Boolean.parseBoolean(props.getProperty("verbose", "false"));
    }

    private static void setupLogging() {
        if (!VERBOSE) {
            Logger.getLogger("").setLevel(Level.SEVERE);
            LOGGER.setLevel(Level.SEVERE);
        }
    }

    private static File validateFile(String path, boolean mustExist) {
        File file = new File(path);
        if (mustExist && (!file.exists() || !file.canRead())) {
            throw new IllegalArgumentException("IO_DENIED: Asset not accessible at " + path);
        }
        return file;
    }

    private static File validateDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) throw new IllegalArgumentException("PATH_INVALID: " + path);
        return dir;
    }

    private static void validateArgs(String[] args, int minLength, String usage) {
        if (args.length < minLength) throw new IllegalArgumentException("SYNTAX: lumatrace " + usage);
    }

    private static File generateOutputFile(File input) {
        return new File(input.getParent(), "PROT_" + input.getName());
    }

    private static void printUsage() {
        System.out.println("LumaTrace CLI System [v" + VERSION + "]");
        System.out.println("Architecture: Adaptive Spread-Spectrum / Multi-threaded Batch");
        System.out.println("\nCommands:");
        System.out.println("  embed <in> [out]     Signal injection");
        System.out.println("  detect <in>          Signal extraction & validation");
        System.out.println("  benchmark <in>       Forensic resilience suite");
        System.out.println("  batch <dir>          High-throughput processing");
        System.out.println("  keygen               Cryptographic seed derivation");
    }

    private static void printEmbedTelemetry(File in, File out, long ms, int w, int h) {
        double mp = (w * h) / 1_000_000.0;
        System.out.println("\n[EMBED_METRICS]");
        System.out.printf("Asset:      %s [%.2f MPixels]\n", in.getName(), mp);
        System.out.printf("Result:     %s\n", out.getName());
        System.out.printf("Latency:    %d ms\n", ms);
        System.out.printf("Net_Speed:  %.2f MP/s\n", mp / (ms / 1000.0));
    }

    private static void printDetectionReport(WatermarkDetector.DetectionResult res, File in, long ms) {
        System.out.println("\n--- FORENSIC_ANALYSIS_REPORT ---");
        System.out.printf("Target:     %s\n", in.getName());
        System.out.printf("Signal:     %s\n", res.detected() ? "VALIDATED" : "NOT_DETECTED");
        System.out.printf("Z_Score:    σ = %.4f\n", res.confidenceZ());
        System.out.printf("Scale_Est:  %.2fx\n", res.detectedScale());
        System.out.printf("Analysis:   %d ms\n", ms);
        System.out.println("--------------------------------");
    }

    private static void showVersion() {
        System.out.println("LumaTrace Core V" + VERSION);
        System.out.println("Environment: Java " + System.getProperty("java.version"));
    }
}