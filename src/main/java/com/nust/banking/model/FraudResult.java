package com.nust.banking.model;

import java.time.Instant;

/**
 * Immutable record representing the evaluation outcome of a fraud detection rule.
 * Demonstrates clean encapsulation and modern Java immutable value objects.
 */
public record FraudResult(
    String transactionId,
    FraudStatus status,
    String ruleTriggered,
    double riskScore,
    Instant timestamp
) {
    public enum FraudStatus {
        CLEARED,
        FLAGGED_SUSPICIOUS,
        QUARANTINED
    }

    public static FraudResult cleared(String transactionId) {
        return new FraudResult(transactionId, FraudStatus.CLEARED, "NONE", 0.0, Instant.now());
    }

    public static FraudResult flagged(String transactionId, String ruleName, double score) {
        return new FraudResult(transactionId, FraudStatus.FLAGGED_SUSPICIOUS, ruleName, score, Instant.now());
    }

    public boolean isActionable() {
        return status == FraudStatus.FLAGGED_SUSPICIOUS || status == FraudStatus.QUARANTINED;
    }
}
