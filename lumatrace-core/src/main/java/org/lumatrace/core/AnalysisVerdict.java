package org.lumatrace.core;

/**
 * Enumeration of signal integrity states based on statistical Z-Score (Sigma).
 * Defines the operational thresholds for automated content validation.
 */
public enum AnalysisVerdict {
    /** High statistical certainty of signal presence (Sigma >= 4.0) */
    VERIFIED,

    /** Low signal-to-noise ratio; integrity cannot be guaranteed (3.0 <= Sigma < 4.0) */
    MARGINAL,

    /** Signal absent or below the reliable detection floor (Sigma < 3.0) */
    INCONCLUSIVE;

    /**
     * Evaluates the statistical significance of a detection result.
     * * @param sigma The computed Z-Score from the cross-correlation engine.
     * @return The corresponding validation state.
     */
    public static AnalysisVerdict evaluate(double sigma) {
        if (sigma >= 4.0) return VERIFIED;
        if (sigma >= 3.0) return MARGINAL;
        return INCONCLUSIVE;
    }
}