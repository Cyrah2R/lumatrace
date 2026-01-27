package org.lumatrace.core;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.logging.Logger;

/**
 * High-Performance Blind Watermark Detector v3.1 (Production Ready)
 * Zero-allocation hot paths, coarse-to-fine search, adaptive thresholds.
 */
public class WatermarkDetector {

    private static final Logger LOG = Logger.getLogger(WatermarkDetector.class.getName());

    // Search configuration
    private static final double[] HEURISTIC_SCALES = {1.0, 0.75, 0.5, 1.25, 1.5};
    private static final double MIN_SCALE = 0.5;
    private static final double MAX_SCALE = 2.0;
    private static final double SCALE_STEP = 0.05;

    // Detection parameters
    private static final double BASE_Z_THRESHOLD = 4.0;
    private static final double EARLY_EXIT_Z = 8.0;
    private static final int TILE_SIZE = WatermarkEngine.TILE_SIZE;

    // Performance tuning
    private static final int MIN_IMAGE_SIZE_FOR_PARALLEL = 512 * 512;
    private static final int COARSE_SEARCH_STEP = 4;
    private static final int REFINE_SEARCH_RADIUS = 3;

    // Thread-local buffers (zero allocation after warmup)
    private static final ThreadLocal<ProcessingContext> LOCAL_CONTEXT =
            ThreadLocal.withInitial(ProcessingContext::new);

    // Signature statistics cache (seed -> stats)
    private static final ConcurrentHashMap<Long, SignatureStats> SIG_STATS_CACHE =
            new ConcurrentHashMap<>();

    public record DetectionResult(
            boolean detected,
            double confidenceZ,
            double detectedScale,
            long processingTimeMs,
            double correlationScore
    ) {
        @Override
        public String toString() {
            return String.format("Detection[%s z=%.2f scale=%.2f time=%dms]",
                    detected ? "✅" : "❌", confidenceZ, detectedScale, processingTimeMs);
        }
    }

    /**
     * Main detection entry point.
     */
    public DetectionResult detect(BufferedImage targetImage,
                                  long masterKey,
                                  String userId,
                                  String contentId) {

        final long startTime = System.currentTimeMillis();

        if (targetImage == null) {
            throw new IllegalArgumentException("Target image cannot be null");
        }

        final int width = targetImage.getWidth();
        final int height = targetImage.getHeight();
        final long pixelCount = (long) width * height;

        LOG.fine(() -> String.format("Detecting watermark in %dx%d (%.1f MP)",
                width, height, pixelCount / 1_000_000.0));

        // Generate signature from keys
        final long seed = KeyDerivation.deriveSeed(masterKey, userId, contentId);
        final WatermarkEngine engine = new WatermarkEngine();
        final double[][] signature = engine.generateSignature(seed);

        // Get signature statistics (cached)
        final SignatureStats sigStats = getSignatureStats(signature, seed);

        // Adaptive threshold based on image size
        final double adaptiveThreshold = computeAdaptiveThreshold(pixelCount);

        // Phase 1: Heuristic search (fast)
        DetectionResult bestResult = heuristicSearch(
                targetImage, signature, sigStats, startTime, adaptiveThreshold);

        if (bestResult.confidenceZ() >= EARLY_EXIT_Z) {
            LOG.fine("Early exit: high confidence detected");
            return bestResult;
        }

        // Phase 2: Fine-grained search if needed
        if (!bestResult.detected() || bestResult.confidenceZ() < adaptiveThreshold * 0.8) {
            List<Double> fineScales = generateFineScales();
            DetectionResult deepResult = deepSearch(
                    targetImage, signature, sigStats, fineScales,
                    startTime, adaptiveThreshold, pixelCount);

            if (deepResult.confidenceZ() > bestResult.confidenceZ()) {
                bestResult = deepResult;
            }
        }

        final long totalTime = System.currentTimeMillis() - startTime;
        DetectionResult finalBestResult = bestResult;
        LOG.fine(() -> String.format("Detection completed in %d ms, z=%.2f",
                totalTime, finalBestResult.confidenceZ()));

        return bestResult;
    }

    /**
     * Fast heuristic search at predefined scales.
     */
    private DetectionResult heuristicSearch(BufferedImage image,
                                            double[][] signature,
                                            SignatureStats sigStats,
                                            long startTime,
                                            double threshold) {

        DetectionResult best = new DetectionResult(false, -Double.MAX_VALUE, 1.0, 0, 0.0);

        for (double scale : HEURISTIC_SCALES) {
            DetectionResult result = analyzeScale(image, signature, sigStats,
                    scale, startTime, threshold);
            if (result.confidenceZ() > best.confidenceZ()) {
                best = result;
            }
        }

        return best;
    }

    /**
     * Deep search across many scales.
     */
    private DetectionResult deepSearch(BufferedImage image,
                                       double[][] signature,
                                       SignatureStats sigStats,
                                       List<Double> scales,
                                       long startTime,
                                       double threshold,
                                       long pixelCount) {

        if (pixelCount > MIN_IMAGE_SIZE_FOR_PARALLEL && scales.size() > 4) {
            return parallelScaleSearch(image, signature, sigStats,
                    scales, startTime, threshold);
        } else {
            return sequentialScaleSearch(image, signature, sigStats,
                    scales, startTime, threshold);
        }
    }

    /**
     * Analyze image at specific scale.
     */
    private DetectionResult analyzeScale(BufferedImage original,
                                         double[][] signature,
                                         SignatureStats sigStats,
                                         double scale,
                                         long startTime,
                                         double threshold) {

        final ProcessingContext context = LOCAL_CONTEXT.get();

        try {
            // Calculate target dimensions
            final int targetW = Math.max(TILE_SIZE, (int) (original.getWidth() * scale));
            final int targetH = Math.max(TILE_SIZE, (int) (original.getHeight() * scale));

            // Skip if too small
            if (targetW < TILE_SIZE || targetH < TILE_SIZE) {
                return new DetectionResult(false, 0.0, scale,
                        System.currentTimeMillis() - startTime, 0.0);
            }

            // Resize and extract pixels
            final BufferedImage resized = resizeImage(original, targetW, targetH);
            final int[] pixels = extractPixels(resized);

            // Fold image into tile
            final double[][] folded = foldImage(pixels, targetW, targetH, context);

            // Compute correlation
            final double correlation = computeCorrelation(folded, signature, sigStats, context);

            // Convert to Z-score
            final double zScore = correlationToZScore(correlation, targetW, targetH);

            return new DetectionResult(
                    zScore >= threshold,
                    zScore,
                    scale,
                    System.currentTimeMillis() - startTime,
                    correlation
            );

        } finally {
            context.clear();
        }
    }

    /**
     * Parallel scale search using ForkJoinPool.
     */
    private DetectionResult parallelScaleSearch(BufferedImage image,
                                                double[][] signature,
                                                SignatureStats sigStats,
                                                List<Double> scales,
                                                long startTime,
                                                double threshold) {

        return ForkJoinPool.commonPool().invoke(new RecursiveTask<DetectionResult>() {
            @Override
            protected DetectionResult compute() {
                return searchRange(0, scales.size());
            }

            private DetectionResult searchRange(int start, int end) {
                if (end - start <= 3) { // Base case: process small batch
                    DetectionResult best = new DetectionResult(false, -Double.MAX_VALUE, 1.0, 0, 0.0);
                    for (int i = start; i < end; i++) {
                        DetectionResult result = analyzeScale(
                                image, signature, sigStats,
                                scales.get(i), startTime, threshold);
                        if (result.confidenceZ() > best.confidenceZ()) {
                            best = result;
                        }
                    }
                    return best;
                }

                // Split and process in parallel
                int mid = (start + end) / 2;
                RecursiveTask<DetectionResult> leftTask = new RecursiveTask<>() {
                    protected DetectionResult compute() { return searchRange(start, mid); }
                };
                RecursiveTask<DetectionResult> rightTask = new RecursiveTask<>() {
                    protected DetectionResult compute() { return searchRange(mid, end); }
                };

                leftTask.fork();
                DetectionResult rightResult = rightTask.compute();
                DetectionResult leftResult = leftTask.join();

                return rightResult.confidenceZ() > leftResult.confidenceZ()
                        ? rightResult : leftResult;
            }
        });
    }

    /**
     * Sequential scale search.
     */
    private DetectionResult sequentialScaleSearch(BufferedImage image,
                                                  double[][] signature,
                                                  SignatureStats sigStats,
                                                  List<Double> scales,
                                                  long startTime,
                                                  double threshold) {

        DetectionResult best = new DetectionResult(false, -Double.MAX_VALUE, 1.0, 0, 0.0);

        for (double scale : scales) {
            DetectionResult result = analyzeScale(
                    image, signature, sigStats, scale, startTime, threshold);
            if (result.confidenceZ() > best.confidenceZ()) {
                best = result;
            }
        }

        return best;
    }

    /**
     * Fold image into TILE_SIZE x TILE_SIZE with high-pass filtering.
     */
    private double[][] foldImage(int[] pixels, int width, int height,
                                 ProcessingContext context) {

        final double[][] folded = context.foldBuffer;
        final int[][] counts = context.countBuffer;

        // Reset buffers (manual zeroing faster than Arrays.fill for 2D)
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                folded[x][y] = 0.0;
                counts[x][y] = 0;
            }
        }

        // High-pass filtering and accumulation
        for (int y = 1; y < height - 1; y++) {
            final int row = y * width;
            final int upRow = (y - 1) * width;
            final int downRow = (y + 1) * width;

            for (int x = 1; x < width - 1; x++) {
                // Extract center pixel luminance
                final double center = getLuma(pixels[row + x]);

                // Compute Laplacian (center - average of 4 neighbors)
                final double neighbors =
                        getLuma(pixels[upRow + x]) +     // up
                                getLuma(pixels[downRow + x]) +   // down
                                getLuma(pixels[row + x - 1]) +   // left
                                getLuma(pixels[row + x + 1]);    // right

                final double signal = center - neighbors * 0.25;

                // Accumulate into folded tile
                final int tx = x % TILE_SIZE;
                final int ty = y % TILE_SIZE;
                folded[tx][ty] += signal;
                counts[tx][ty]++;
            }
        }

        // Normalize by count
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                final int count = counts[x][y];
                if (count > 0) {
                    folded[x][y] /= count;
                }
            }
        }

        return folded;
    }

    /**
     * Compute correlation between folded image and signature.
     */
    private double computeCorrelation(double[][] folded,
                                      double[][] signature,
                                      SignatureStats sigStats,
                                      ProcessingContext context) {

        // Coarse search (step 4)
        double bestCorr = -1.0;
        int bestDx = 0, bestDy = 0;

        for (int dy = 0; dy < TILE_SIZE; dy += COARSE_SEARCH_STEP) {
            for (int dx = 0; dx < TILE_SIZE; dx += COARSE_SEARCH_STEP) {
                final double corr = computeShiftCorrelation(
                        folded, signature, sigStats, dx, dy);
                if (corr > bestCorr) {
                    bestCorr = corr;
                    bestDx = dx;
                    bestDy = dy;
                }
            }
        }

        // Refinement search around best coarse position
        for (int ddy = -REFINE_SEARCH_RADIUS; ddy <= REFINE_SEARCH_RADIUS; ddy++) {
            for (int ddx = -REFINE_SEARCH_RADIUS; ddx <= REFINE_SEARCH_RADIUS; ddx++) {
                if (ddx == 0 && ddy == 0) continue; // Already checked

                final int ndx = (bestDx + ddx + TILE_SIZE) % TILE_SIZE;
                final int ndy = (bestDy + ddy + TILE_SIZE) % TILE_SIZE;

                final double corr = computeShiftCorrelation(
                        folded, signature, sigStats, ndx, ndy);
                if (corr > bestCorr) {
                    bestCorr = corr;
                }
            }
        }

        return bestCorr;
    }

    /**
     * Compute correlation for specific shift.
     */
    private double computeShiftCorrelation(double[][] folded,
                                           double[][] signature,
                                           SignatureStats sigStats,
                                           int dx, int dy) {

        double dotProduct = 0.0;
        double foldSum = 0.0;
        double foldSumSq = 0.0;

        for (int y = 0; y < TILE_SIZE; y++) {
            final int sy = (y + dy) % TILE_SIZE;
            for (int x = 0; x < TILE_SIZE; x++) {
                final int sx = (x + dx) % TILE_SIZE;

                final double foldVal = folded[x][y];
                final double sigVal = signature[sx][sy] - sigStats.mean;

                foldSum += foldVal;
                foldSumSq += foldVal * foldVal;
                dotProduct += foldVal * sigVal;
            }
        }

        // Compute correlation coefficient
        final int n = TILE_SIZE * TILE_SIZE;
        final double foldMean = foldSum / n;
        final double foldVar = foldSumSq / n - foldMean * foldMean;

        if (foldVar <= 0.0 || sigStats.std <= 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(foldVar) * sigStats.std * n);
    }

    /**
     * Convert correlation to Z-score (statistical significance).
     */
    private double correlationToZScore(double correlation, int width, int height) {
        if (Math.abs(correlation) >= 0.999) {
            return 999.0;
        }

        // Fisher z-transform
        final double fisherZ = 0.5 * Math.log((1 + correlation) / (1 - correlation));

        // Standard error depends on effective sample size
        final double effectiveSamples = (width * height) / (double) (TILE_SIZE * TILE_SIZE);
        final double stdError = 1.0 / Math.sqrt(effectiveSamples - 3);

        return Math.abs(fisherZ / stdError);
    }

    /**
     * Compute adaptive detection threshold.
     */
    private double computeAdaptiveThreshold(long pixelCount) {
        if (pixelCount > 10_000_000L) { // Large image: more statistical power
            return BASE_Z_THRESHOLD * 0.9;
        } else if (pixelCount < 100_000L) { // Small image: less reliable
            return BASE_Z_THRESHOLD * 1.2;
        }
        return BASE_Z_THRESHOLD;
    }

    /**
     * Generate fine-grained scales for deep search.
     */
    private List<Double> generateFineScales() {
        final List<Double> scales = new ArrayList<>();

        for (double s = MIN_SCALE; s <= MAX_SCALE; s += SCALE_STEP) {
            boolean tooClose = false;
            for (double h : HEURISTIC_SCALES) {
                if (Math.abs(s - h) < SCALE_STEP * 0.5) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                scales.add(s);
            }
        }

        return scales;
    }

    /**
     * Get signature statistics (cached).
     */
    private SignatureStats getSignatureStats(double[][] signature, long seed) {
        return SIG_STATS_CACHE.computeIfAbsent(seed, k -> {
            double sum = 0.0, sumSq = 0.0;

            for (int y = 0; y < TILE_SIZE; y++) {
                for (int x = 0; x < TILE_SIZE; x++) {
                    final double val = signature[x][y];
                    sum += val;
                    sumSq += val * val;
                }
            }

            final int n = TILE_SIZE * TILE_SIZE;
            final double mean = sum / n;
            final double variance = sumSq / n - mean * mean;
            final double std = Math.sqrt(Math.max(variance, 0.0));

            return new SignatureStats(mean, std);
        });
    }

    /**
     * Resize image with bilinear interpolation.
     */
    private BufferedImage resizeImage(BufferedImage src, int width, int height) {
        final BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    /**
     * Extract pixel data efficiently.
     */
    private int[] extractPixels(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_RGB &&
                img.getRaster().getDataBuffer() instanceof DataBufferInt db) {
            return db.getData();
        }
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    /**
     * Compute luminance (Rec. 709) from RGB.
     */
    private double getLuma(int rgb) {
        final int r = (rgb >> 16) & 0xFF;
        final int g = (rgb >> 8) & 0xFF;
        final int b = rgb & 0xFF;
        return r * 0.2126 + g * 0.7152 + b * 0.0722;
    }

    // =========================================================================
    // HELPER CLASSES
    // =========================================================================

    /**
     * Thread-local processing context for zero-allocation operations.
     */
    private static final class ProcessingContext {
        final double[][] foldBuffer = new double[TILE_SIZE][TILE_SIZE];
        final int[][] countBuffer = new int[TILE_SIZE][TILE_SIZE];

        void clear() {
            // Buffers will be overwritten, no need to zero them here
        }
    }

    /**
     * Precomputed signature statistics.
     */
    private record SignatureStats(double mean, double std) {}
}