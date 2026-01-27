package org.lumatrace.core;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Adaptive Spatial Watermarking Engine.
 * Implements a hybrid Spread-Spectrum injection mechanism with psychovisual
 * masking and local texture-based gain modulation.
 */
public class WatermarkEngine {

    public static final int TILE_SIZE = 64;

    // Perceptual Masking Thresholds (JND - Just Noticeable Difference)
    private static final double MIN_GAIN = 12.0;
    private static final double MAX_GAIN = 35.0;
    private static final double ENTROPY_NORMALIZATION = 30.0;

    // Spectral Weighting (Hybrid Low-Frequency/High-Frequency signature)
    // LF provides resilience against resampling; HF ensures statistical uniqueness
    private static final double LF_WEIGHT = 0.85;
    private static final double HF_WEIGHT = 0.15;

    // Rec. 709 Luminance Coefficients for high-precision luma modeling
    private static final double LUMA_R = 0.2126;
    private static final double LUMA_G = 0.7152;
    private static final double LUMA_B = 0.0722;

    // Chrominance Injection Vectors (Primary B-Y manifold injection)
    private static final double VEC_BLUE = 0.85;
    private static final double VEC_RED = 0.10;
    private static final double VEC_GREEN = 0.05;

    /**
     * Generates a deterministic high-entropy signature using a hybrid frequency approach.
     * @param seed Cryptographically derived 64-bit seed.
     * @return 2D signal matrix (tiled signature).
     */
    public double[][] generateSignature(long seed) {
        Random rng = new Random(seed);
        double[][] sig = new double[TILE_SIZE][TILE_SIZE];

        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                // Low-frequency component (4x4 spatial correlation for rescaling resilience)
                double lf = new Random(seed + (x / 4) * 1000 + (y / 4)).nextGaussian();
                // High-frequency component (White Gaussian Noise for uniqueness)
                double hf = rng.nextGaussian();
                sig[x][y] = (LF_WEIGHT * lf) + (HF_WEIGHT * hf);
            }
        }
        return sig;
    }

    /**
     * Injects a robust, imperceptible watermark into the target asset.
     * Uses an adaptive gain controller based on local spatial activity (Entropy Mapping).
     */
    public BufferedImage embedWatermark(BufferedImage image, long masterKey, String userId, String contentId) {
        int w = image.getWidth();
        int h = image.getHeight();
        long seed = KeyDerivation.deriveSeed(masterKey, userId, contentId);
        double[][] signature = generateSignature(seed);

        int[] src = image.getRGB(0, 0, w, h, null, 0, w);
        int[] dst = new int[src.length];

        // Computational Pre-pass: Luminance plane extraction
        double[][] luma = new double[w][h];
        for (int i = 0; i < src.length; i++) {
            int rgb = src[i];
            luma[i % w][i / w] = ((rgb >> 16) & 0xFF) * LUMA_R +
                    ((rgb >> 8) & 0xFF) * LUMA_G +
                    (rgb & 0xFF) * LUMA_B;
        }

        // Main processing loop: Perceptual gain modulation and signal injection
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;

                // Spatial Activity Analysis (First-order derivative approximation)
                double entropy = 0;
                if (x < w - 1 && y < h - 1) {
                    entropy = Math.abs(luma[x][y] - luma[x + 1][y]) +
                            Math.abs(luma[x][y] - luma[x][y + 1]);
                }

                // Adaptive Gain Controller (Modulates signal based on local texture complexity)
                double gain = MIN_GAIN + Math.min(1.0, entropy / ENTROPY_NORMALIZATION) * (MAX_GAIN - MIN_GAIN);
                double signal = signature[x % TILE_SIZE][y % TILE_SIZE] * gain;

                int r = (src[i] >> 16) & 0xFF;
                int g = (src[i] >> 8) & 0xFF;
                int b = src[i] & 0xFF;

                // Differential chrominance injection
                dst[i] = (0xFF << 24) |
                        (clamp(r + signal * VEC_RED) << 16) |
                        (clamp(g + signal * VEC_GREEN) << 8) |
                        clamp(b + signal * VEC_BLUE);
            }
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, dst, 0, w);
        return out;
    }

    private int clamp(double value) {
        int i = (int) Math.round(value);
        return i < 0 ? 0 : Math.min(255, i);
    }
}