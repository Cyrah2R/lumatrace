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
 * Enterprise CLI for LumaTrace V3 - Production Ready
 */
public class LumaTraceCLI {

    private static final Logger LOGGER = Logger.getLogger(LumaTraceCLI.class.getName());
    private static final String CONFIG_FILE = "lumatrace.properties";
    private static final String VERSION = "3.0.0";

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
                printHelp();
                System.exit(1);
            }

            printBanner();

            int exitCode = switch (args[0].toLowerCase()) {
                case "embed", "-e" -> processEmbed(args);
                case "detect", "-d" -> processDetect(args);
                case "benchmark", "-b" -> processBenchmark(args);
                case "batch" -> processBatch(args);
                case "keygen", "-g" -> generateKey();
                case "version", "-v" -> { showVersion(); yield 0; }
                default -> { printHelp(); yield 1; }
            };

            System.exit(exitCode);

        } catch (IllegalArgumentException e) {
            System.err.println("❌ Argument error: " + e.getMessage());
            printHelp();
            System.exit(2);
        } catch (Exception e) {
            System.err.println("💥 Processing error: " + e.getMessage());
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

        if (input.equals(output)) {
            throw new IllegalArgumentException("Input and output cannot be the same file");
        }

        LOGGER.info(() -> String.format("Embedding %s -> %s", input.getName(), output.getName()));

        BufferedImage src = null;
        try {
            src = ImageIO.read(input);
            if (src == null) throw new IllegalArgumentException("Unsupported image format");

            WatermarkEngine engine = new WatermarkEngine();
            long start = System.nanoTime();
            BufferedImage result = engine.embedWatermark(src, MASTER_KEY, DEFAULT_USER, DEFAULT_CONTENT);
            long duration = (System.nanoTime() - start) / 1_000_000;

            saveJpeg(result, output, JPEG_QUALITY);

            printEmbedSuccess(input, output, duration, src.getWidth(), src.getHeight());
            return 0;

        } finally {
            if (src != null) src.flush();
        }
    }

    private static int processDetect(String[] args) throws Exception {
        validateArgs(args, 2, "detect <input>");

        File input = validateFile(args[1], true);

        LOGGER.info(() -> "Detecting watermark in: " + input.getName());

        BufferedImage img = null;
        try {
            img = ImageIO.read(input);
            if (img == null) throw new IllegalArgumentException("Unsupported image format");

            WatermarkDetector detector = new WatermarkDetector();
            long start = System.nanoTime();
            WatermarkDetector.DetectionResult result = detector.detect(img, MASTER_KEY, DEFAULT_USER, DEFAULT_CONTENT);
            long duration = (System.nanoTime() - start) / 1_000_000;

            printDetectionResult(result, input, duration);
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
            System.err.println("Benchmark failed: " + e.getMessage());
            return 3;
        }
    }

    private static int processBatch(String[] args) {
        validateArgs(args, 2, "batch <input-dir> [output-dir]");

        File inDir = validateDirectory(args[1]);
        File outDir = args.length > 2
                ? new File(args[2])
                : new File(inDir.getParentFile(), inDir.getName() + "_watermarked");

        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalArgumentException("Cannot create output directory: " + outDir);
        }

        File[] files = inDir.listFiles((d, n) ->
                n.toLowerCase().matches(".*\\.(jpg|jpeg|png|bmp)$"));

        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No image files found in directory");
        }

        System.out.printf("\n📦 Processing %d images\n", files.length);
        System.out.println("─────────────────────────────");

        int success = 0, failed = 0;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (File file : files) {
                executor.submit(() -> {
                    try {
                        BufferedImage src = ImageIO.read(file);
                        if (src != null) {
                            try {
                                WatermarkEngine engine = new WatermarkEngine();
                                BufferedImage result = engine.embedWatermark(
                                        src, MASTER_KEY, DEFAULT_USER, DEFAULT_CONTENT);

                                File output = new File(outDir,
                                        file.getName().replaceFirst("\\.[^.]+$", "") + "_watermarked.jpg");

                                saveJpeg(result, output, JPEG_QUALITY);
                                System.out.printf("✅ %s\n", file.getName());
                                return true;
                            } finally {
                                src.flush();
                            }
                        }
                        return false;
                    } catch (Exception e) {
                        System.err.printf("❌ %s: %s\n", file.getName(), e.getMessage());
                        return false;
                    }
                });
            }
        } // Executor auto-closes

        System.out.printf("\n📊 Summary: %d successful, %d failed\n", success, failed);
        System.out.printf("📁 Output: %s\n", outDir.getAbsolutePath());

        return failed == 0 ? 0 : 6;
    }

    private static int generateKey() {
        SecureRandom rnd = new SecureRandom();
        long key = rnd.nextLong();
        System.out.printf("\n" +
                        "🔑 Generated Secure Master Key\n" +
                        "─────────────────────────────\n" +
                        "Decimal: %d\n" +
                        "Hex:     0x%X\n" +
                        "\n" +
                        "📋 Add to lumatrace.properties:\n" +
                        "   master.key=0x%X\n" +
                        "\n" +
                        "⚠️  Store securely - cannot be recovered!\n",
                key, key, key);
        return 0;
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private static void saveJpeg(BufferedImage img, File file, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            ImageIO.write(img, "jpg", file);
            return;
        }

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

        // Load from file
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (InputStream is = new FileInputStream(configFile)) {
                props.load(is);
            } catch (Exception e) {
                LOGGER.warning("Cannot read config file: " + e.getMessage());
            }
        }

        // Environment overrides
        String envKey = System.getenv("LUMATRACE_MASTER_KEY");
        if (envKey != null) {
            props.setProperty("master.key", envKey);
            LOGGER.info("Using master key from environment");
        }

        // Parse values with defaults
        String keyStr = props.getProperty("master.key", "123456789");
        try {
            MASTER_KEY = keyStr.startsWith("0x")
                    ? Long.parseUnsignedLong(keyStr.substring(2), 16)
                    : Long.parseLong(keyStr);
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid master key format, using default");
            MASTER_KEY = 123456789L;
        }

        DEFAULT_USER = props.getProperty("default.user", "demo-user");
        DEFAULT_CONTENT = props.getProperty("default.content", "demo-content");
        JPEG_QUALITY = Float.parseFloat(props.getProperty("jpeg.quality", "0.90"));
        VERBOSE = Boolean.parseBoolean(props.getProperty("verbose", "false"));

        // Security warning for demo key
        if (MASTER_KEY == 123456789L) {
            System.err.println("\n⚠️  WARNING: Using default demo key!");
            System.err.println("   Create 'lumatrace.properties' with master.key=YOUR_KEY");
            System.err.println("   Or set LUMATRACE_MASTER_KEY environment variable\n");
        }
    }

    private static void setupLogging() {
        if (!VERBOSE) {
            Logger.getLogger("").setLevel(Level.WARNING);
            LOGGER.setLevel(Level.INFO);
        }
    }

    private static File validateFile(String path, boolean mustExist) {
        File file = new File(path);
        if (mustExist && !file.exists()) {
            throw new IllegalArgumentException("File not found: " + path);
        }
        if (mustExist && !file.canRead()) {
            throw new IllegalArgumentException("Cannot read file: " + path);
        }
        return file;
    }

    private static File validateDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Directory not found: " + path);
        }
        if (!dir.canRead()) {
            throw new IllegalArgumentException("Cannot read directory: " + path);
        }
        return dir;
    }

    private static void validateArgs(String[] args, int minLength, String usage) {
        if (args.length < minLength) {
            throw new IllegalArgumentException("Invalid arguments. Usage: " + usage);
        }
    }

    private static File generateOutputFile(File input) {
        String name = input.getName().replaceFirst("\\.[^.]+$", "");
        String timestamp = String.valueOf(System.currentTimeMillis() % 1000000);
        return new File(input.getParentFile(), name + "_watermarked_" + timestamp + ".jpg");
    }

    private static void printBanner() {
        System.out.println("\n" +
                "┌─────────────────────────────────────────────────────┐\n" +
                "│                  LumaTrace v" + VERSION + "                       │\n" +
                "│         Enterprise Watermarking Framework          │\n" +
                "└─────────────────────────────────────────────────────┘\n");
    }

    private static void printHelp() {
        System.out.println("\n📖 LumaTrace - Command Line Interface");
        System.out.println("─────────────────────────────────────");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  embed <input> [output]    Embed watermark into image");
        System.out.println("  detect <input>            Detect watermark in image");
        System.out.println("  benchmark [input]         Run robustness tests");
        System.out.println("  batch <input-dir> [output-dir]  Process directory");
        System.out.println("  keygen                    Generate secure master key");
        System.out.println("  version                   Show version information");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar lumatrace.jar embed photo.jpg");
        System.out.println("  java -jar lumatrace.jar detect secured.jpg");
        System.out.println("  java -jar lumatrace.jar batch ./photos");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Create 'lumatrace.properties' or use environment variables:");
        System.out.println("    master.key=0xDEADBEEF12345678");
        System.out.println("    default.user=your@email.com");
        System.out.println("    jpeg.quality=0.90");
        System.out.println();
    }

    private static void printEmbedSuccess(File input, File output, long duration, int width, int height) {
        double mpixels = (width * height) / 1_000_000.0;
        double speed = mpixels / (duration / 1000.0);

        System.out.printf("\n" +
                        "✅ EMBEDDING SUCCESSFUL\n" +
                        "─────────────────────────────\n" +
                        "Input:    %s (%dx%d, %.1f MP)\n" +
                        "Output:   %s\n" +
                        "Time:     %d ms (%.1f MPixels/s)\n" +
                        "Key:      0x%X\n" +
                        "\n" +
                        "📌 Next: detect %s\n",
                input.getName(), width, height, mpixels,
                output.getName(),
                duration, speed,
                MASTER_KEY,
                output.getName());
    }

    private static void printDetectionResult(WatermarkDetector.DetectionResult result,
                                             File input, long duration) {
        System.out.printf("\n" +
                        "🔍 DETECTION REPORT: %s\n" +
                        "─────────────────────────────\n" +
                        "Result:   %s\n" +
                        "Confidence: σ = %.2f\n" +
                        "Scale:     %.2fx\n" +
                        "Time:      %d ms\n",
                input.getName(),
                result.detected() ? "✅ WATERMARK DETECTED" : "❌ NO WATERMARK",
                result.confidenceZ(),
                result.detectedScale(),
                duration);

        if (result.detected()) {
            if (result.confidenceZ() > 15.0) {
                System.out.println("💪 Strong signal - survives heavy compression");
            } else if (result.confidenceZ() < 5.0) {
                System.out.println("⚠️  Weak signal - check for compression/editing");
            }
        }
    }

    private static void showVersion() {
        System.out.println("\nLumaTrace v" + VERSION);
        System.out.println("Java " + System.getProperty("java.version"));
        System.out.println("Build: " + System.getProperty("java.runtime.version"));
    }
}