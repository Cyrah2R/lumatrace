package org.lumatrace.core;

/**
 * Immutable Data Carrier for detection results.
 * Uses Java 21 Records for concise, thread-safe data modeling.
 */
public record DetectionReport(
        double sigma,
        double scale,
        long executionTimeMs,
        DetectionVerdict verdict
) {
    public DetectionReport(double sigma, double scale, long executionTimeMs) {
        this(sigma, scale, executionTimeMs, DetectionVerdict.fromSigma(sigma));
    }

    @Override
    public String toString() {
        return String.format("Sigma: %.2f | Scale: %.2fx | Verdict: %s | Time: %dms",
                sigma, scale, verdict, executionTimeMs);
    }
}