package org.lumatrace.core;

/*
 * Copyright (c) 2026 David Rivera Ruz.
 * Licensed under the GNU General Public License v3.0.
 *
 * High-Performance Forensic Signal Detector.
 * Platform Agnostic: Works on Cloud (JVM) and Mobile (Android).
 * Implements a dual-phase spatial synchronization engine using 2D cross-correlation.
 */
public class WatermarkDetector {

    private static final int TILE_SIZE = 64;
    private static final int PHASE_SEARCH_STEP = 1;
    // El umbral ya está definido dentro de AnalysisVerdict, pero lo dejamos aquí por si acaso se usa en lógica interna
    private static final double SIGMA_DETECTION_FLOOR = 4.0;

    /**
     * Executes forensic analysis on raw pixel data.
     * Returns the external DetectionReport.
     */
    public DetectionReport detect(int[] pixels, int width, int height, long masterKey, String userId, String contentId) {
        long startTime = System.currentTimeMillis();

        long seed = KeyDerivation.deriveSeed(masterKey, userId, contentId);
        double[][] signature = new WatermarkEngine().generateSignature(seed);

        double bestZ = 0;
        double bestS = 1.0;

        // Multi-scale synchronization scan
        double[] targetScales = {1.0, 0.75, 0.5, 1.25};

        for (double s : targetScales) {
            double z = analyzeScale(pixels, width, height, signature, s);
            if (z > bestZ) {
                bestZ = z;
                bestS = s;
            }
            // Optimization: SNR early exit
            if (bestZ > 15.0) break;
        }

        long latency = System.currentTimeMillis() - startTime;

        // CORRECCIÓN: Usamos el constructor de 3 argumentos (Sigma, Scale, Latency).
        // El 'AnalysisVerdict' se calcula automáticamente dentro del Record.
        return new DetectionReport(bestZ, bestS, latency);
    }

    private double analyzeScale(int[] originalPixels, int w, int h, double[][] signature, double scale) {
        int targetW = (int) (w * scale);
        int targetH = (int) (h * scale);

        if (targetW < TILE_SIZE || targetH < TILE_SIZE) return 0;

        // 1. Resize Layer (Pure Math Implementation)
        int[] processedPixels;
        if (scale == 1.0) {
            processedPixels = originalPixels;
        } else {
            processedPixels = resizeBilinear(originalPixels, w, h, targetW, targetH);
        }

        double[][] folded = new double[TILE_SIZE][TILE_SIZE];
        int[][] counts = new int[TILE_SIZE][TILE_SIZE];

        // 2. Signal Integration (Folding)
        for (int y = 0; y < targetH; y++) {
            for (int x = 0; x < targetW; x++) {
                double signal = extractDifferentialChrominance(processedPixels[y * targetW + x]);
                folded[x % TILE_SIZE][y % TILE_SIZE] += signal;
                counts[x % TILE_SIZE][y % TILE_SIZE]++;
            }
        }

        // Mean signal normalization
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                if (counts[x][y] > 0) folded[x][y] /= counts[x][y];
            }
        }

        return maximizeCrossCorrelation(folded, signature, targetW, targetH);
    }

    private int[] resizeBilinear(int[] pixels, int w, int h, int w2, int h2) {
        int[] newPixels = new int[w2 * h2];
        int a, b, c, d, x, y, index;
        float x_ratio = ((float) (w - 1)) / w2;
        float y_ratio = ((float) (h - 1)) / h2;
        float x_diff, y_diff, blue, red, green;
        int offset = 0;

        for (int i = 0; i < h2; i++) {
            for (int j = 0; j < w2; j++) {
                x = (int) (x_ratio * j);
                y = (int) (y_ratio * i);
                x_diff = (x_ratio * j) - x;
                y_diff = (y_ratio * i) - y;
                index = (y * w + x);
                a = pixels[index];
                b = pixels[index + 1];
                c = pixels[index + w];
                d = pixels[index + w + 1];

                blue = (a & 0xff) * (1 - x_diff) * (1 - y_diff) + (b & 0xff) * (x_diff) * (1 - y_diff) +
                        (c & 0xff) * (y_diff) * (1 - x_diff) + (d & 0xff) * (x_diff * y_diff);

                green = ((a >> 8) & 0xff) * (1 - x_diff) * (1 - y_diff) + ((b >> 8) & 0xff) * (x_diff) * (1 - y_diff) +
                        ((c >> 8) & 0xff) * (y_diff) * (1 - x_diff) + ((d >> 8) & 0xff) * (x_diff * y_diff);

                red = ((a >> 16) & 0xff) * (1 - x_diff) * (1 - y_diff) + ((b >> 16) & 0xff) * (x_diff) * (1 - y_diff) +
                        ((c >> 16) & 0xff) * (y_diff) * (1 - x_diff) + ((d >> 16) & 0xff) * (x_diff * y_diff);

                newPixels[offset++] = 0xFF000000 | ((((int) red) << 16) & 0xff0000) | ((((int) green) << 8) & 0xff00) | ((int) blue);
            }
        }
        return newPixels;
    }

    private double extractDifferentialChrominance(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        double luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return (double) b - luma;
    }

    private double maximizeCrossCorrelation(double[][] folded, double[][] signature, int w, int h) {
        double maxCorr = 0;
        for (int dy = 0; dy < TILE_SIZE; dy += PHASE_SEARCH_STEP) {
            for (int dx = 0; dx < TILE_SIZE; dx += PHASE_SEARCH_STEP) {
                maxCorr = Math.max(maxCorr, computePearsonCorrelation(folded, signature, dx, dy));
            }
        }
        double n = (w * h) / (double) (TILE_SIZE * TILE_SIZE);
        return maxCorr * Math.sqrt(n) * 10;
    }

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