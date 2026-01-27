package org.lumatrace.core;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Industrial-grade Spread-Spectrum Watermarking Engine v2.2
 * Optimized for Java 21 with complete edge handling and efficient pool management.
 */
public class WatermarkEngine {

    private static final Logger LOG = Logger.getLogger(WatermarkEngine.class.getName());

    // Algorithm constants
    public static final int TILE_SIZE = 64;
    public static final int MACRO_BLOCK_SIZE = 4;
    private static final int BLOCKS_PER_TILE = TILE_SIZE / MACRO_BLOCK_SIZE;

    // Psychovisual parameters
    private static final double MIN_GAIN = 6.0;
    private static final double MAX_GAIN = 20.0;
    private static final double ACTIVITY_NORMALIZATION = 25.0;

    // Luma coefficients (Rec. 709)
    private static final double LUMA_RED = 0.2126;
    private static final double LUMA_GREEN = 0.7152;
    private static final double LUMA_BLUE = 0.0722;

    // Channel injection weights
    private static final double INJECT_BLUE = 0.8;
    private static final double INJECT_RED = 0.2;
    private static final double INJECT_GREEN = 0.1;

    // Performance tuning
    private static final int MIN_IMAGE_DIMENSION = TILE_SIZE;
    private static final int PARALLEL_THRESHOLD = 512 * 512; // 0.25MP

    // Shared ForkJoinPool (singleton per JVM)
    private static final ForkJoinPool SHARED_POOL = new ForkJoinPool();

    // Thread-safe signature cache
    private static class SignatureCache {
        final long seed;
        final double[][] signature;
        final long creationTime;

        SignatureCache(long seed, double[][] signature) {
            this.seed = seed;
            this.signature = signature;
            this.creationTime = System.currentTimeMillis();
        }

        boolean isValid() {
            return System.currentTimeMillis() - creationTime < 300000;
        }
    }

    private static volatile SignatureCache globalCache;

    /**
     * Generates deterministic Gaussian noise signature.
     */
    public synchronized double[][] generateSignature(long seed) {
        if (globalCache != null && globalCache.seed == seed && globalCache.isValid()) {
            return globalCache.signature;
        }

        double[][] signature = new double[TILE_SIZE][TILE_SIZE];
        Random rng = new Random(seed);

        // Pre-calculate all macro-block values
        double[][] macroBlockValues = new double[BLOCKS_PER_TILE][BLOCKS_PER_TILE];
        for (int by = 0; by < BLOCKS_PER_TILE; by++) {
            for (int bx = 0; bx < BLOCKS_PER_TILE; bx++) {
                macroBlockValues[bx][by] = rng.nextGaussian();
            }
        }

        // Fill signature with cache-friendly pattern
        for (int y = 0; y < TILE_SIZE; y++) {
            int blockY = y / MACRO_BLOCK_SIZE;
            for (int x = 0; x < TILE_SIZE; x++) {
                int blockX = x / MACRO_BLOCK_SIZE;
                signature[x][y] = macroBlockValues[blockX][blockY];
            }
        }

        globalCache = new SignatureCache(seed, signature);
        return signature;
    }

    /**
     * Embeds watermark with thread-safe parallel processing.
     */
    public BufferedImage embedWatermark(
            BufferedImage image,
            long masterKey,
            String userId,
            String contentId) {

        validateImageForWatermarking(image);

        int width = image.getWidth();
        int height = image.getHeight();
        long totalPixels = (long) width * height;

        LOG.info(String.format("Embedding into %dx%d image (%.1f MP)",
                width, height, totalPixels / 1_000_000.0));

        long totalStartTime = System.currentTimeMillis();

        long seed = KeyDerivation.deriveSeed(masterKey, userId, contentId);
        double[][] signature = generateSignature(seed);

        int[] srcPixels = extractPixelData(image);
        int[] dstPixels = new int[srcPixels.length];

        // Pipeline: Luminance -> Activity -> Embedding
        double[][] luminance = calculateLuminanceMap(srcPixels, width, height);
        double[][] activityMap = calculateActivityMap(luminance, width, height);

        LongAdder highActivityPixels = new LongAdder();
        long embeddingStart = System.currentTimeMillis();

        if (totalPixels > PARALLEL_THRESHOLD) {
            parallelEmbed(srcPixels, dstPixels, signature, activityMap,
                    width, height, highActivityPixels);
        } else {
            sequentialEmbed(srcPixels, dstPixels, signature, activityMap,
                    width, height, highActivityPixels);
        }

        long embeddingTime = System.currentTimeMillis() - embeddingStart;

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        output.setRGB(0, 0, width, height, dstPixels, 0, width);

        logEmbeddingMetrics(width, height, embeddingTime, totalStartTime,
                highActivityPixels.sum(), totalPixels);

        return output;
    }

    private void sequentialEmbed(int[] src, int[] dst, double[][] signature,
                                 double[][] activity, int width, int height,
                                 LongAdder highActivityCounter) {
        embedRegion(src, dst, signature, activity, width, 0, height, highActivityCounter);
    }

    private void parallelEmbed(int[] src, int[] dst, double[][] signature,
                               double[][] activity, int width, int height,
                               LongAdder highActivityCounter) {
        final int stripeHeight = Math.max(64, height / (Runtime.getRuntime().availableProcessors() * 4));

        SHARED_POOL.invoke(new RecursiveAction() {
            @Override
            protected void compute() {
                embedStripes(0, height, stripeHeight);
            }

            private void embedStripes(int startY, int endY, int stripeSize) {
                if (endY - startY <= stripeSize) {
                    embedRegion(src, dst, signature, activity, width, startY, endY, highActivityCounter);
                } else {
                    int midY = (startY + endY) / 2;
                    invokeAll(
                            new RecursiveAction() {
                                protected void compute() { embedStripes(startY, midY, stripeSize); }
                            },
                            new RecursiveAction() {
                                protected void compute() { embedStripes(midY, endY, stripeSize); }
                            }
                    );
                }
            }
        });
    }

    /**
     * Core embedding logic for any region.
     */
    private void embedRegion(int[] src, int[] dst, double[][] signature,
                             double[][] activity, int width,
                             int startY, int endY, LongAdder highActivityCounter) {
        final double activityThreshold = (MIN_GAIN + MAX_GAIN) / 2.0;

        for (int y = startY; y < endY; y++) {
            int rowStart = y * width;
            for (int x = 0; x < width; x++) {
                int idx = rowStart + x;
                int rgb = src[idx];

                // Extract RGB directly (no intermediate variables)
                double gain = computeAdaptiveGain(activity[x][y]);
                if (gain > activityThreshold) {
                    highActivityCounter.increment();
                }

                double signal = signature[x % TILE_SIZE][y % TILE_SIZE] * gain;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                dst[idx] = (clampToByte(r + signal * INJECT_RED) << 16) |
                        (clampToByte(g + signal * INJECT_GREEN) << 8) |
                        clampToByte(b + signal * INJECT_BLUE);
            }
        }
    }

    private double[][] calculateLuminanceMap(int[] pixels, int width, int height) {
        double[][] luminance = new double[width][height];
        for (int y = 0; y < height; y++) {
            int rowStart = y * width;
            for (int x = 0; x < width; x++) {
                int rgb = pixels[rowStart + x];
                luminance[x][y] = ((rgb >> 16) & 0xFF) * LUMA_RED +
                        ((rgb >> 8) & 0xFF) * LUMA_GREEN +
                        (rgb & 0xFF) * LUMA_BLUE;
            }
        }
        return luminance;
    }

    /**
     * Complete activity calculation including borders.
     */
    private double[][] calculateActivityMap(double[][] luminance, int width, int height) {
        double[][] activity = new double[width][height];

        // Interior pixels (have both right and down neighbors)
        for (int y = 0; y < height - 1; y++) {
            for (int x = 0; x < width - 1; x++) {
                double center = luminance[x][y];
                activity[x][y] = Math.abs(center - luminance[x + 1][y]) +
                        Math.abs(center - luminance[x][y + 1]);
            }
        }

        // Last column (has down neighbor but no right)
        for (int y = 0; y < height - 1; y++) {
            double center = luminance[width - 1][y];
            activity[width - 1][y] = Math.abs(center - luminance[width - 1][y + 1]);
        }

        // Last row (has right neighbor but no down)
        for (int x = 0; x < width - 1; x++) {
            double center = luminance[x][height - 1];
            activity[x][height - 1] = Math.abs(center - luminance[x + 1][height - 1]);
        }

        // Bottom-right corner (no neighbors) - use average of left and up
        if (width > 1 && height > 1) {
            activity[width - 1][height - 1] =
                    (activity[width - 2][height - 1] + activity[width - 1][height - 2]) / 2.0;
        }

        return activity;
    }

    private int[] extractPixelData(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB && image.getRaster().getDataBuffer() instanceof DataBufferInt) {
            return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        }
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private double computeAdaptiveGain(double activity) {
        double normalized = Math.min(1.0, activity / ACTIVITY_NORMALIZATION);
        return MIN_GAIN + Math.pow(normalized, 0.7) * (MAX_GAIN - MIN_GAIN);
    }

    private int clampToByte(double value) {
        int rounded = (int) Math.round(value);
        return rounded < 0 ? 0 : rounded > 255 ? 255 : rounded;
    }

    private void validateImageForWatermarking(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("Input image cannot be null");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        if (width < MIN_IMAGE_DIMENSION || height < MIN_IMAGE_DIMENSION) {
            throw new IllegalArgumentException(String.format(
                    "Image dimensions (%dx%d) below minimum %dx%d. " +
                            "Watermark requires sufficient pixel area for reliable embedding.",
                    width, height, MIN_IMAGE_DIMENSION, MIN_IMAGE_DIMENSION));
        }

        long pixelCount = (long) width * height;
        if (pixelCount > 500_000_000L) { // 500MP limit
            throw new IllegalArgumentException(
                    "Image too large (" + pixelCount + " pixels). Maximum: 500MP");
        }
    }

    private void logEmbeddingMetrics(int width, int height, long embeddingTime,
                                     long totalStartTime, long highActivityPixels,
                                     long totalPixels) {
        long totalTime = System.currentTimeMillis() - totalStartTime;
        double speed = (totalPixels / 1_000_000.0) / (embeddingTime / 1000.0);
        double highActivityPercent = (highActivityPixels * 100.0) / totalPixels;

        LOG.info(String.format("Embedding completed in %d ms (speed: %.1f MPixels/s)",
                totalTime, speed));

        if (LOG.isLoggable(java.util.logging.Level.FINE)) {
            LOG.fine(String.format("High-activity pixels: %.1f%%", highActivityPercent));
            if (highActivityPercent < 20.0) {
                LOG.fine("Note: Low-texture image - watermark may be less robust");
            }
        }
    }

    public String getConfiguration() {
        return String.format(
                "LumaTrace Watermark Engine v2.2\n" +
                        "  Tile Size: %dx%d | Gain Range: %.1f-%.1f\n" +
                        "  Parallel Threshold: %d pixels\n" +
                        "  Pool: Shared ForkJoinPool (%d threads)",
                TILE_SIZE, TILE_SIZE, MIN_GAIN, MAX_GAIN,
                PARALLEL_THRESHOLD,
                Runtime.getRuntime().availableProcessors()
        );
    }

    /**
     * Clean shutdown for the shared pool.
     * Call this when application shuts down.
     */
    public static void shutdown() {
        SHARED_POOL.shutdown();
        try {
            if (!SHARED_POOL.awaitTermination(5, TimeUnit.SECONDS)) {
                SHARED_POOL.shutdownNow();
            }
        } catch (InterruptedException e) {
            SHARED_POOL.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Alternative: Lazy initialization with cleaner
     */
    private static ForkJoinPool getSharedPool() {
        return SharedPoolHolder.POOL;
    }

    private static class SharedPoolHolder {
        static final ForkJoinPool POOL = new ForkJoinPool();

        static {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                POOL.shutdown();
                try {
                    if (!POOL.awaitTermination(3, TimeUnit.SECONDS)) {
                        POOL.shutdownNow();
                    }
                } catch (InterruptedException ignored) {
                }
            }));
        }
    }
}