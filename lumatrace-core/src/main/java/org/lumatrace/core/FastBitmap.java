package org.lumatrace.core;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

/**
 * High-performance wrapper for direct byte-level image manipulation.
 * Avoids the overhead of BufferedImage.getRGB() for O(n) operations.
 */
public class FastBitmap {

    private final int width;
    private final int height;
    private final byte[] data;
    private final int pixelStride;
    private final int rowStride;

    // Psychovisual weights for luma calculation (Rec. 601)
    private static final double LUMA_R = 0.299;
    private static final double LUMA_G = 0.587;
    private static final double LUMA_B = 0.114;

    public FastBitmap(BufferedImage image) {
        // Standardize image type to 3BYTE_BGR for consistent byte layout
        BufferedImage standardized = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_3BYTE_BGR
        );
        Graphics2D g = standardized.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        this.width = standardized.getWidth();
        this.height = standardized.getHeight();
        // Direct access to the underlying raster data array
        this.data = ((DataBufferByte) standardized.getRaster().getDataBuffer()).getData();

        this.pixelStride = 3; // BGR format
        this.rowStride = width * pixelStride;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * Extracts the watermark signal from a specific pixel coordinate, applying
     * local activity normalization to reduce host signal interference.
     *
     * @param x Horizontal coordinate
     * @param y Vertical coordinate
     * @return The normalized signal value (approximate blue channel deviation)
     */
    public double getSignal(int x, int y) {
        int index = (y * rowStride) + (x * pixelStride);

        // Java bytes are signed (-128 to 127), mask with 0xFF to get unsigned 0-255
        int b = data[index] & 0xFF;
        int g = data[index + 1] & 0xFF;
        int r = data[index + 2] & 0xFF;

        // Signal extraction model: Blue channel carries 80% of the payload
        double rawSignal = (r * 0.2 + g * 0.1 + b * 0.8) - 128.0;

        // Local Activity Estimation (Edge Masking)
        // We look ahead to calculate simple gradient without full convolution matrix
        if (x < width - 1 && y < height - 1) {
            int idxRight = index + pixelStride;
            int idxDown = index + rowStride;

            double luma = calculateLuma(r, g, b);
            double lumaRight = calculateLuma(
                    data[idxRight + 2] & 0xFF,
                    data[idxRight + 1] & 0xFF,
                    data[idxRight] & 0xFF
            );
            double lumaDown = calculateLuma(
                    data[idxDown + 2] & 0xFF,
                    data[idxDown + 1] & 0xFF,
                    data[idxDown] & 0xFF
            );

            double activity = Math.abs(luma - lumaRight) + Math.abs(luma - lumaDown);

            // Normalize signal by local complexity
            return rawSignal / (1.0 + activity * 0.05);
        }

        return rawSignal;
    }

    private double calculateLuma(int r, int g, int b) {
        return r * LUMA_R + g * LUMA_G + b * LUMA_B;
    }
}