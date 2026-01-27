package org.lumatrace.core;

/**
 * Forensic analysis report containing signal metrics and statistical significance.
 * Implemented as a Java 21 Record for immutability and high-concurrency safety.
 */
public record DetectionReport(
        double confidenceSigma,
        double estimatedScale,
        long latencyMs,
        AnalysisVerdict status
) {
    /**
     * Canonical constructor for automated status derivation based on statistical significance.
     */
    public DetectionReport(double confidenceSigma, double estimatedScale, long latencyMs) {
        this(confidenceSigma, estimatedScale, latencyMs, AnalysisVerdict.evaluate(confidenceSigma));
    }

    /**
     * Serializes telemetry for industrial logging or console output.
     */
    @Override
    public String toString() {
        return String.format("[TELEMETRY] Confidence: σ=%.4f | Scale: %.2fx | Status: %s | Latency: %dms",
                confidenceSigma, estimatedScale, status, latencyMs);
    }
}