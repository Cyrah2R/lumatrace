package org.lumatrace.core;

import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;

public class WatermarkEngine {

    public static final int TILE_SIZE = 64;
    private static final double MIN_GAIN = 12.0; // Incrementado para robustez
    private static final double MAX_GAIN = 35.0;
    private static final double ACTIVITY_NORMALIZATION = 30.0;

    private static final double LF_WEIGHT = 0.85; // Mayor peso a baja frecuencia (aguanta re-escalado)
    private static final double HF_WEIGHT = 0.15;

    private static final double LUMA_RED = 0.2126;
    private static final double LUMA_GREEN = 0.7152;
    private static final double LUMA_BLUE = 0.0722;

    private static final double INJECT_BLUE = 0.85;
    private static final double INJECT_RED = 0.10;
    private static final double INJECT_GREEN = 0.05;

    private static final ForkJoinPool SHARED_POOL = new ForkJoinPool();

    public double[][] generateSignature(long seed) {
        Random rng = new Random(seed);
        double[][] sig = new double[TILE_SIZE][TILE_SIZE];

        // Firma híbrida optimizada
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                double lf = new Random(seed + (x/4)*1000 + (y/4)).nextGaussian();
                double hf = rng.nextGaussian();
                sig[x][y] = LF_WEIGHT * lf + HF_WEIGHT * hf;
            }
        }
        return sig;
    }

    public BufferedImage embedWatermark(BufferedImage image, long masterKey, String userId, String contentId) {
        int w = image.getWidth();
        int h = image.getHeight();
        long seed = KeyDerivation.deriveSeed(masterKey, userId, contentId);
        double[][] signature = generateSignature(seed);

        int[] src = image.getRGB(0, 0, w, h, null, 0, w);
        int[] dst = new int[src.length];

        // Pre-cálculo de mapa de actividad para ganancia adaptativa
        double[][] luma = new double[w][h];
        for (int i = 0; i < src.length; i++) {
            int rgb = src[i];
            luma[i % w][i / w] = ((rgb >> 16) & 0xFF) * LUMA_RED + ((rgb >> 8) & 0xFF) * LUMA_GREEN + (rgb & 0xFF) * LUMA_BLUE;
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                double act = 0;
                if (x < w - 1 && y < h - 1) {
                    act = Math.abs(luma[x][y] - luma[x+1][y]) + Math.abs(luma[x][y] - luma[x][y+1]);
                }

                double gain = MIN_GAIN + Math.min(1.0, act / ACTIVITY_NORMALIZATION) * (MAX_GAIN - MIN_GAIN);
                double s = signature[x % TILE_SIZE][y % TILE_SIZE] * gain;

                int r = (src[i] >> 16) & 0xFF;
                int g = (src[i] >> 8) & 0xFF;
                int b = src[i] & 0xFF;

                dst[i] = (0xFF << 24) | (clamp(r + s * INJECT_RED) << 16) | (clamp(g + s * INJECT_GREEN) << 8) | clamp(b + s * INJECT_BLUE);
            }
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, dst, 0, w);
        return out;
    }

    private int clamp(double v) {
        int i = (int) Math.round(v);
        return i < 0 ? 0 : Math.min(255, i);
    }
}