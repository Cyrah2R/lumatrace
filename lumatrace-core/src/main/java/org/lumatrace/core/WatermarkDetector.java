package org.lumatrace.core;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * High-Performance Forensic Signal Detector.
 * * Implements a dual-phase spatial synchronization engine using 2D cross-correlation.
 * Designed to recover spread-spectrum signatures under geometric distortion,
 * including interpolation (scaling) and spatial translation (clipping).
 */
public class WatermarkDetector {

    private static final int TILE_SIZE = 64;
    private static final int PHASE_SEARCH_STEP = 1; // Unit-pixel precision for spatial offset recovery
    private static final double SIGMA_DETECTION_FLOOR = 4.0; // Statistical confidence threshold

    /**
     * Analytical telemetry for signal detection events.
     */
    public record DetectionResult(boolean detected, double confidenceZ, double detectedScale) {}

    /**
     * Executes forensic analysis on a target asset to isolate and validate a cryptographic signature.
     * * @param img       Suspect digital asset
     * @param masterKey Root 64-bit security key
     * @param userId    Originator identity
     * @param contentId Asset payload identifier
     * @return Forensic validation report
     */
    public DetectionResult detect(BufferedImage img, long masterKey, String userId, String contentId) {
        long seed = KeyDerivation.deriveSeed(masterKey, userId, contentId);
        double[][] signature = new WatermarkEngine().generateSignature(seed);

        double bestZ = 0;
        double bestS = 1.0;

        // Multi-scale synchronization scan for interpolation attack mitigation
        double[] targetScales = {1.0, 0.75, 0.5, 1.25};
        for (double s : targetScales) {
            double z = analyzeScale(img, signature, s);
            if (z > bestZ) {
                bestZ = z;
                bestS = s;
            }
            // Optimization: SNR early exit on high-confidence signal match
            if (bestZ > 15.0) break;
        }

        return new DetectionResult(bestZ >= SIGMA_DETECTION_FLOOR, bestZ, bestS);
    }

    private double analyzeScale(BufferedImage original, double[][] signature, double scale) {
        int w = (int) (original.getWidth() * scale);
        int h = (int) (original.getHeight() * scale);

        if (w < TILE_SIZE || h < TILE_SIZE) return 0;

        // Bilinear resynchronization layer
        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, w, h, null);
        g.dispose();

        int[] pixels = resized.getRGB(0, 0, w, h, null, 0, w);
        double[][] folded = new double[TILE_SIZE][TILE_SIZE];
        int[][] counts = new int[TILE_SIZE][TILE_SIZE];

        // Signal integration and spatial folding (Tiling reconstruction)
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double signal = extractDifferentialChrominance(pixels[y * w + x]);
                folded[x % TILE_SIZE][y % TILE_SIZE] += signal;
                counts[x % TILE_SIZE][y % TILE_SIZE]++;
            }
        }

        // Mean signal normalization across tiling space
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                if (counts[x][y] > 0) folded[x][y] /= counts[x][y];
            }
        }

        return maximizeCrossCorrelation(folded, signature, w, h);
    }

    /**
     * Differential chrominance extraction.
     * Operates on the B-Y (Blue minus Luma) manifold to maximize Signal-to-Noise Ratio (SNR)
     * while maintaining psychovisual imperceptibility.
     */
    private double extractDifferentialChrominance(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        // Rec. 709 Luma coefficients for high-precision luminance modeling
        double luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return (double) b - luma;
    }

    /**
     * Iterative 2D phase-shift search to maximize signal correlation.
     * Corrects spatial translation offsets common in cropping and alignment attacks.
     */
    private double maximizeCrossCorrelation(double[][] folded, double[][] signature, int w, int h) {
        double maxCorr = 0;

        for (int dy = 0; dy < TILE_SIZE; dy += PHASE_SEARCH_STEP) {
            for (int dx = 0; dx < TILE_SIZE; dx += PHASE_SEARCH_STEP) {
                double corr = computePearsonCorrelation(folded, signature, dx, dy);
                maxCorr = Math.max(maxCorr, corr);
            }
        }

        // Statistical conversion to Z-Score based on sampling density and tile redundancy
        double n = (w * h) / (double)(TILE_SIZE * TILE_SIZE);
        return maxCorr * Math.sqrt(n) * 10;
    }

    /**
     * Pearson Product-Moment Correlation implementation.
     * Measures the linear relationship between extracted signal and reference signature.
     */
    private double computePearsonCorrelation(double[][] a, double[][] b, int dx, int dy) {
        double dot = 0, sumA = 0, sumB = 0, sumA2 = 0, sumB2 = 0;
        int n = TILE_SIZE * TILE_SIZE;
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                double va = a[x][y];
                double vb = b[(x + dx) % TILE_SIZE][(y + dy) % TILE_SIZE];
                dot += va * vb;
                sumA += va; sumB += vb;
                sumA2 += va * va; sumB2 += vb * vb;
            }
        }
        double num = n * dot - sumA * sumB;
        double den = Math.sqrt((n * sumA2 - sumA * sumA) * (n * sumB2 - sumB * sumB));
        return (den == 0) ? 0 : num / den;
    }
}