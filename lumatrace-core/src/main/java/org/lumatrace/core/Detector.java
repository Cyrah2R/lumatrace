package org.lumatrace.core;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.stream.IntStream;

/**
 * Blind watermark detector using multi-scale correlation analysis.
 */
public class Detector {

    private static final double[] HEURISTIC_SCALES = {1.0, 0.9, 0.8, 0.75};
    private static final double MIN_SEARCH_SCALE = 0.5;
    private static final double SCALE_STEP = 0.05;

    private static final double EARLY_EXIT_SIGMA = 20.0;
    private static final double Z_SCORE_CUTOFF = 2.5;

    public static DetectionReport analyzeImage(
            File file,
            long masterKey,
            String userId,
            String contentId
    ) throws IOException {

        long start = System.currentTimeMillis();

        BufferedImage img = ImageIO.read(file);
        if (img == null) {
            throw new IOException("Unsupported or corrupt image: " + file.getName());
        }

        WatermarkEngine engine = new WatermarkEngine();
        long seed = KeyDerivation.deriveSeed(masterKey, userId, contentId);
        double[][] signature = engine.generateSignature(seed);

        double bestSigma = -1;
        double bestScale = 1.0;

        for (double scale : HEURISTIC_SCALES) {
            double sigma = analyzeAtScale(img, signature, scale);
            if (sigma > bestSigma) {
                bestSigma = sigma;
                bestScale = scale;
            }
            if (bestSigma >= EARLY_EXIT_SIGMA) {
                return new DetectionReport(bestSigma, bestScale,
                        System.currentTimeMillis() - start);
            }
        }

        for (double scale = 0.70; scale >= MIN_SEARCH_SCALE; scale -= SCALE_STEP) {
            double sigma = analyzeAtScale(img, signature, scale);
            if (sigma > bestSigma) {
                bestSigma = sigma;
                bestScale = scale;
            }
        }

        return new DetectionReport(bestSigma, bestScale,
                System.currentTimeMillis() - start);
    }

    private static double analyzeAtScale(
            BufferedImage original,
            double[][] signature,
            double scale
    ) {
        int w = (int) (original.getWidth() * scale);
        int h = (int) (original.getHeight() * scale);

        BufferedImage resized = resize(original, w, h);
        FastBitmap img = new FastBitmap(resized);

        return computeAccumulatedEnergy(img, signature);
    }

    private static double computeAccumulatedEnergy(
            FastBitmap img,
            double[][] signature
    ) {
        int tile = WatermarkEngine.TILE_SIZE;
        int total = tile * tile;
        double[] scores = new double[total];

        IntStream.range(0, total).parallel().forEach(i -> {
            int ox = i % tile;
            int oy = i / tile;

            double[][] folded = new double[tile][tile];
            double[][] count = new double[tile][tile];

            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int tx = (x + ox) % tile;
                    int ty = (y + oy) % tile;
                    folded[tx][ty] += img.getSignal(x, y);
                    count[tx][ty]++;
                }
            }

            double corr = 0;
            double n = 0;

            for (int y = 0; y < tile; y++) {
                for (int x = 0; x < tile; x++) {
                    if (count[x][y] > 0) {
                        corr += (folded[x][y] / count[x][y]) * signature[x][y];
                        n++;
                    }
                }
            }

            scores[i] = n > 0 ? corr / n : 0.0;
        });

        return robustSigma(scores);
    }

    private static double robustSigma(double[] scores) {
        double mean = 0;
        for (double v : scores) mean += v;
        mean /= scores.length;

        double var = 0;
        for (double v : scores) var += (v - mean) * (v - mean);
        var /= scores.length;

        double std = Math.sqrt(var);
        if (std < 1e-9) return 0;

        double energy = 0;
        int contributors = 0;

        for (double v : scores) {
            double z = (v - mean) / std;
            if (z > Z_SCORE_CUTOFF) {
                energy += z;
                contributors++;
            }
        }

        if (contributors <= 1) {
            double max = scores[0];
            for (double v : scores) max = Math.max(max, v);
            return (max - mean) / std;
        }

        return energy / Math.sqrt(contributors);
    }

    private static BufferedImage resize(BufferedImage img, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
