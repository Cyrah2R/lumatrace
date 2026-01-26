package org.lumatrace.core;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Implements the core <b>Spread-Spectrum Watermarking</b> algorithm using
 * adaptive Blue-channel injection.
 *
 * The watermark signal is modulated by local image activity (gradient energy)
 * to preserve perceptual invisibility in low-texture regions.
 */
public class WatermarkEngine {

    public static final int TILE_SIZE = 64;

    // --- PSYCHOVISUAL TUNING CONSTANTS ---
    private static final double MIN_GAIN = 6.0;
    private static final double MAX_GAIN = 20.0;
    private static final double ACTIVITY_NORMALIZATION_FACTOR = 25.0;

    // --- LUMA WEIGHTS (Rec. 601) ---
    private static final double LUMA_RED = 0.299;
    private static final double LUMA_GREEN = 0.587;
    private static final double LUMA_BLUE = 0.114;

    // --- CHANNEL INJECTION WEIGHTS ---
    private static final double INJECT_BLUE = 0.8;
    private static final double INJECT_RED = 0.2;
    private static final double INJECT_GREEN = 0.1;

    private static final int MACRO_BLOCK_SIZE = 4;

    public double[][] generateSignature(long seed) {
        Random rnd = new Random(seed);
        double[][] signature = new double[TILE_SIZE][TILE_SIZE];

        for (int y = 0; y < TILE_SIZE; y += MACRO_BLOCK_SIZE) {
            for (int x = 0; x < TILE_SIZE; x += MACRO_BLOCK_SIZE) {
                double noise = rnd.nextGaussian();
                fillMacroBlock(signature, x, y, noise);
            }
        }
        return signature;
    }

    public BufferedImage embedWatermark(
            BufferedImage image,
            long masterKey,
            String userId,
            String contentId
    ) {
        long seed = KeyDerivation.deriveSeed(masterKey, userId, contentId);

        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        double[][] signature = generateSignature(seed);
        double[][] activityMap = computeLocalActivity(image);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                double gain = calculateAdaptiveGain(activityMap[x][y]);
                double signal = signature[x % TILE_SIZE][y % TILE_SIZE] * gain;

                int nr = clamp(r + signal * INJECT_RED);
                int ng = clamp(g + signal * INJECT_GREEN);
                int nb = clamp(b + signal * INJECT_BLUE);

                output.setRGB(x, y, (nr << 16) | (ng << 8) | nb);
            }
        }
        return output;
    }

    // --- INTERNAL HELPERS ---

    private void fillMacroBlock(double[][] matrix, int sx, int sy, double value) {
        for (int dy = 0; dy < MACRO_BLOCK_SIZE; dy++) {
            for (int dx = 0; dx < MACRO_BLOCK_SIZE; dx++) {
                matrix[sx + dx][sy + dy] = value;
            }
        }
    }

    /**
     * Computes local activity using a simple gradient-energy approximation.
     * This is not Shannon entropy; the term "activity" is used deliberately.
     */
    private double[][] computeLocalActivity(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        double[][] activity = new double[w][h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double c = luma(img.getRGB(x, y));
                double r = luma(img.getRGB(Math.min(x + 1, w - 1), y));
                double d = luma(img.getRGB(x, Math.min(y + 1, h - 1)));

                activity[x][y] = Math.abs(c - r) + Math.abs(c - d);
            }
        }
        return activity;
    }

    private double calculateAdaptiveGain(double activity) {
        double factor = Math.min(1.0, activity / ACTIVITY_NORMALIZATION_FACTOR);
        return MIN_GAIN + factor * (MAX_GAIN - MIN_GAIN);
    }

    private double luma(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return r * LUMA_RED + g * LUMA_GREEN + b * LUMA_BLUE;
    }

    private int clamp(double v) {
        return v < 0 ? 0 : Math.min(255, (int) v);
    }
}
