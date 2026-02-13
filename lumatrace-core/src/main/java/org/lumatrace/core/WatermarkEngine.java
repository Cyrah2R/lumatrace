package org.lumatrace.core;

import java.util.Random;

/*
 * Copyright (c) 2026 David Rivera Ruz.
 * Licensed under the GNU General Public License v3.0.
 *
 * Enterprise Grade Watermark Engine.
 * Platform Agnostic: Works on JVM (Cloud) and Dalvik/ART (Android).
 * NO external dependencies on AWT or Android SDK.
 */
public class WatermarkEngine {

    public static final int TILE_SIZE = 64;

    // JND Thresholds
    private static final double MIN_GAIN = 1.2;
    private static final double MAX_GAIN = 10.0;
    private static final double ENTROPY_NORMALIZATION = 65.0;

    // Spectral Weighting
    private static final double LF_WEIGHT = 0.85;
    private static final double HF_WEIGHT = 0.15;

    // Rec. 709 Luminance
    private static final double LUMA_R = 0.2126;
    private static final double LUMA_G = 0.7152;
    private static final double LUMA_B = 0.0722;

    // Injection Vectors
    private static final double VEC_BLUE = 0.60;
    private static final double VEC_RED = 0.25;
    private static final double VEC_GREEN = 0.15;

    /**
     * Generates a deterministic high-entropy signature.
     */
    public double[][] generateSignature(long seed) {
        Random rng = new Random(seed);
        double[][] sig = new double[TILE_SIZE][TILE_SIZE];

        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                double lf = new Random(seed + (x / 4) * 1000 + (y / 4)).nextGaussian();
                double hf = rng.nextGaussian();
                sig[x][y] = (LF_WEIGHT * lf) + (HF_WEIGHT * hf);
            }
        }
        return sig;
    }

    /**
     * Injects watermark into raw pixel data.
     * Universal method: Compatible with Java AWT (Server) and Android Bitmaps.
     *
     * @param pixels Array of pixels in ARGB format (0xAARRGGBB).
     * @param w Image width.
     * @param h Image height.
     * @return The modified pixel array (new instance).
     */
    public int[] embedWatermark(int[] pixels, int w, int h, long masterKey, String userId, String contentId) {
        long seed = KeyDerivation.deriveSeed(masterKey, userId, contentId);
        double[][] signature = generateSignature(seed);

        int[] dst = new int[pixels.length];

        // 1. Pre-calculate Luminance Plane (Optimization for performance)
        double[] luma = new double[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int rgb = pixels[i];
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            luma[i] = r * LUMA_R + g * LUMA_G + b * LUMA_B;
        }

        // 2. Main Injection Loop
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;

                // Calculate Local Entropy (Edge Detection)
                double entropy = 0;
                if (x < w - 1 && y < h - 1) {
                    double current = luma[i];
                    entropy = Math.abs(current - luma[i + 1]) +       // Horizontal gradient
                            Math.abs(current - luma[i + w]);        // Vertical gradient
                }

                // Adaptive Gain Calculation
                double gain = MIN_GAIN + Math.min(1.0, entropy / ENTROPY_NORMALIZATION) * (MAX_GAIN - MIN_GAIN);
                double signal = signature[x % TILE_SIZE][y % TILE_SIZE] * gain;

                // Original Pixel Extraction
                int rgb = pixels[i];
                int a = (rgb >> 24) & 0xFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Apply Signal
                dst[i] = (a << 24) |
                        (clamp(r + signal * VEC_RED) << 16) |
                        (clamp(g + signal * VEC_GREEN) << 8) |
                        clamp(b + signal * VEC_BLUE);
            }
        }
        return dst;
    }

    private int clamp(double value) {
        int i = (int) (value + 0.5); // Fast rounding
        if (i < 0) return 0;
        if (i > 255) return 255;
        return i;
    }
}