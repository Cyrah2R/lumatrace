package org.lumatrace.core;

/**
 * Data Transfer Object (DTO) containing the results of a detection analysis.
 * Immutable to ensure thread safety in concurrent environments.
 */
public class DetectionReport {

    private final double sigma;
    private final double scale;
    private final long executionTimeMs;
    private final DetectionVerdict verdict;

    public DetectionReport(double sigma, double scale, long executionTimeMs) {
        this.sigma = sigma;
        this.scale = scale;
        this.executionTimeMs = executionTimeMs;
        this.verdict = DetectionVerdict.fromSigma(sigma);
    }

    public double getSigma() {
        return sigma;
    }

    public double getScale() {
        return scale;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public DetectionVerdict getVerdict() {
        return verdict;
    }

    @Override
    public String toString() {
        return String.format("Sigma: %.2f | Scale: %.2fx | Verdict: %s", sigma, scale, verdict);
    }
}