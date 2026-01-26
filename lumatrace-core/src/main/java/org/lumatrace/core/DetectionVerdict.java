package org.lumatrace.core;

/**
 * Represents the statistical conclusion of the watermark detection process.
 */
public enum DetectionVerdict {
    PASS,
    SUSPICIOUS,
    FAIL;

    /**
     * Determines the verdict based on the Sigma (Z-Score) value.
     * * @param sigma The calculated statistical significance.
     * @return The corresponding verdict.
     */
    public static DetectionVerdict fromSigma(double sigma) {
        if (sigma >= 4.0) return PASS;
        if (sigma >= 3.0) return SUSPICIOUS;
        return FAIL;
    }
}