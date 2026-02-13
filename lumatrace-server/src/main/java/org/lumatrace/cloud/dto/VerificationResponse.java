package org.lumatrace.cloud.dto;

public class VerificationResponse {

    private boolean authentic;
    private double confidenceScore;
    private String message;

    public VerificationResponse(boolean authentic, double confidenceScore, String message) {
        this.authentic = authentic;
        this.confidenceScore = confidenceScore;
        this.message = message;
    }

    public boolean isAuthentic() { return authentic; }
    public double getConfidenceScore() { return confidenceScore; }
    public String getMessage() { return message; }
}