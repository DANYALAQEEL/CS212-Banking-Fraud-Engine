package com.nust.banking.engine;

import com.nust.banking.model.Account;
import com.nust.banking.model.FraudResult;
import com.nust.banking.model.Transaction;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Real-time, thread-safe fraud detection engine enforcing security rules across streaming transactions.
 *
 * <p>Rule Architecture:
 * <ul>
 *   <li><b>Large Amount Rule:</b> Transactions &gt; RS 100,000 trigger elevated scrutiny (+40 risk).</li>
 *   <li><b>High Velocity Rule:</b> More than 5 transfers from the same account within 60s (+35 risk).</li>
 *   <li><b>Suspicious Target Rule:</b> Destination receiving funds from &gt; 3 distinct accounts within 60s (+30 risk).</li>
 *   <li><b>Rapid Drain Rule:</b> Transfer amount exceeding 90% of source account's available balance (+25 risk).</li>
 * </ul>
 *
 * Risk Thresholds:
 * <ul>
 *   <li><b>0 - 39:</b> {@link FraudResult.FraudStatus#CLEARED}</li>
 *   <li><b>40 - 69:</b> {@link FraudResult.FraudStatus#FLAGGED_SUSPICIOUS} (Requires manual audit)</li>
 *   <li><b>70 - 100:</b> {@link FraudResult.FraudStatus#QUARANTINED} (Immediate block)</li>
 * </ul>
 *
 * @author Danyal Aqeel
 */
public class FraudDetectionEngine {

    public static final double LARGE_AMOUNT_THRESHOLD = 100_000.0;
    public static final int VELOCITY_COUNT_THRESHOLD = 5;
    public static final Duration VELOCITY_WINDOW = Duration.ofSeconds(60);
    public static final int SUSPICIOUS_SOURCES_THRESHOLD = 3;
    public static final double RAPID_DRAIN_RATIO_THRESHOLD = 0.90;

    // Thread-safe sliding window tracking maps
    private final Map<String, List<Instant>> sourceVelocityMap = new ConcurrentHashMap<>();
    private final Map<String, List<Transaction>> targetPatternMap = new ConcurrentHashMap<>();

    /**
     * Evaluates a transaction against all fraud rules and returns an immutable {@link FraudResult}.
     *
     * @param transaction transaction to evaluate
     * @param sourceAccount source account instance (may be null if unknown)
     * @return immutable FraudResult token detailing risk score, status, and triggered rules
     */
    public FraudResult evaluateTransaction(Transaction transaction, Account sourceAccount) {
        if (transaction == null) {
            throw new NullPointerException("Cannot evaluate null transaction");
        }

        final Instant now = transaction.timestamp();
        final List<String> triggeredRules = new ArrayList<>();
        double riskScore = 0.0;

        // --- Rule 1: Large Amount Rule ---
        if (transaction.amount() >= LARGE_AMOUNT_THRESHOLD) {
            riskScore += 40.0;
            triggeredRules.add(String.format("LARGE_AMOUNT: Amount RS %.2f exceeds threshold RS %.2f (+40)",
                    transaction.amount(), LARGE_AMOUNT_THRESHOLD));
        }

        // --- Rule 2: High Velocity Rule ---
        final String fromId = transaction.fromAccountId();
        List<Instant> timestamps = sourceVelocityMap.computeIfAbsent(fromId, k -> new CopyOnWriteArrayList<>());
        timestamps.removeIf(t -> Duration.between(t, now).compareTo(VELOCITY_WINDOW) > 0);
        timestamps.add(now);

        if (timestamps.size() > VELOCITY_COUNT_THRESHOLD) {
            riskScore += 35.0;
            triggeredRules.add(String.format("HIGH_VELOCITY: %d transfers detected from %s within 60s (+35)",
                    timestamps.size(), fromId));
        }

        // --- Rule 3: Suspicious Target Pattern ---
        final String toId = transaction.toAccountId();
        List<Transaction> targetTxList = targetPatternMap.computeIfAbsent(toId, k -> new CopyOnWriteArrayList<>());
        targetTxList.removeIf(tx -> Duration.between(tx.timestamp(), now).compareTo(VELOCITY_WINDOW) > 0);
        targetTxList.add(transaction);

        Set<String> distinctSources = new HashSet<>();
        for (Transaction tx : targetTxList) {
            distinctSources.add(tx.fromAccountId());
        }

        if (distinctSources.size() > SUSPICIOUS_SOURCES_THRESHOLD) {
            riskScore += 30.0;
            triggeredRules.add(String.format("SUSPICIOUS_TARGET: Account %s received funds from %d distinct sources within 60s (+30)",
                    toId, distinctSources.size()));
        }

        // --- Rule 4: Rapid Drain Rule ---
        if (sourceAccount != null && sourceAccount.getBalance() > 0) {
            double drainRatio = transaction.amount() / sourceAccount.getBalance();
            if (drainRatio >= RAPID_DRAIN_RATIO_THRESHOLD) {
                riskScore += 25.0;
                triggeredRules.add(String.format("RAPID_DRAIN: Transfer of RS %.2f represents %.1f%% of available balance RS %.2f (+25)",
                        transaction.amount(), drainRatio * 100.0, sourceAccount.getBalance()));
            }
        }

        // Cap risk score at 100.0
        final double finalScore = Math.min(100.0, riskScore);

        // Determine FraudStatus threshold
        final FraudResult.FraudStatus status;
        if (finalScore >= 70.0) {
            status = FraudResult.FraudStatus.QUARANTINED;
        } else if (finalScore >= 40.0) {
            status = FraudResult.FraudStatus.FLAGGED_SUSPICIOUS;
        } else {
            status = FraudResult.FraudStatus.CLEARED;
        }

        String ruleSummary = triggeredRules.isEmpty()
                ? "NONE"
                : String.join("; ", triggeredRules);

        return new FraudResult(transaction.transactionId(), status, ruleSummary, finalScore, Instant.now());
    }

    /**
     * Clears historical sliding window tracking data (useful for test resets).
     */
    public void resetWindowHistory() {
        sourceVelocityMap.clear();
        targetPatternMap.clear();
    }
}
