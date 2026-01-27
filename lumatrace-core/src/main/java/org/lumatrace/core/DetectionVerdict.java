package org.lumatrace.core;

public enum DetectionVerdict {
    PASS,
    SUSPICIOUS,
    FAIL;

    public static DetectionVerdict fromSigma(double sigma) {
        if (sigma >= 4.0) return PASS;
        if (sigma >= 3.0) return SUSPICIOUS;
        return FAIL;
    }
}