package com.nust.banking.ui;

import com.nust.banking.model.Account;
import com.nust.banking.model.FraudResult;
import com.nust.banking.model.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controller and Telemetry Aggregation Engine for Real-Time Security Monitoring.
 *
 * <p>Tracks transaction throughput (TPS), composite system fraud risk scores,
 * lock contention metrics, and security quarantine streams.
 *
 * @author Muhammad Arslan
 */
public class TelemetryViewController implements JavaFXEventListener {

    private final AtomicInteger totalTransactionsProcessed = new AtomicInteger(0);
    private final AtomicInteger flaggedCount = new AtomicInteger(0);
    private final AtomicInteger quarantinedCount = new AtomicInteger(0);

    private final ConcurrentLinkedQueue<Long> timestampQueue = new ConcurrentLinkedQueue<>();
    private final List<String> quarantineLogStream = Collections.synchronizedList(new ArrayList<>());

    /**
     * Records a transaction event for telemetry calculation.
     */
    public void recordEvent(Transaction transaction, FraudResult result) {
        if (transaction == null) return;

        totalTransactionsProcessed.incrementAndGet();
        long now = System.currentTimeMillis();
        timestampQueue.add(now);

        if (result != null) {
            if (result.status() == FraudResult.FraudStatus.FLAGGED_SUSPICIOUS) {
                flaggedCount.incrementAndGet();
                addQuarantineLog(String.format("[%tT] FLAGGED: Tx %s (Score: %.2f) - Rule: %s",
                        now, transaction.transactionId(), result.riskScore(), result.ruleTriggered()));
            } else if (result.status() == FraudResult.FraudStatus.QUARANTINED) {
                quarantinedCount.incrementAndGet();
                addQuarantineLog(String.format("[%tT] QUARANTINED: Tx %s (Score: %.2f) - Rule: %s",
                        now, transaction.transactionId(), result.riskScore(), result.ruleTriggered()));
            }
        }
    }

    private void addQuarantineLog(String message) {
        synchronized (quarantineLogStream) {
            quarantineLogStream.add(0, message);
            while (quarantineLogStream.size() > 200) {
                quarantineLogStream.remove(quarantineLogStream.size() - 1);
            }
        }
    }

    /**
     * Calculates current throughput in Transactions Per Second (TPS) over sliding window.
     */
    public double calculateCurrentTps(long windowMillis) {
        long now = System.currentTimeMillis();
        long cutoff = now - windowMillis;

        timestampQueue.removeIf(ts -> ts < cutoff);

        int count = timestampQueue.size();
        return (count * 1000.0) / Math.max(1, windowMillis);
    }

    /**
     * Calculates composite system risk percentage score (0.0% to 100.0%).
     */
    public double calculateCompositeRiskScore() {
        int total = totalTransactionsProcessed.get();
        if (total == 0) return 0.0;

        int flagged = flaggedCount.get();
        int quarantined = quarantinedCount.get();

        double rawScore = ((flagged * 1.5) + (quarantined * 3.0)) / total * 25.0;
        return Math.min(100.0, Math.max(0.0, rawScore));
    }

    /**
     * Returns human-readable threat level badge based on composite risk score.
     */
    public String getThreatLevelBadge() {
        double score = calculateCompositeRiskScore();
        if (score < 15.0) return "NORMAL / LOW";
        if (score < 40.0) return "ELEVATED THREAT";
        return "CRITICAL / ATTACK DETECTED";
    }

    public int getTotalTransactionsProcessed() {
        return totalTransactionsProcessed.get();
    }

    public int getFlaggedCount() {
        return flaggedCount.get();
    }

    public int getQuarantinedCount() {
        return quarantinedCount.get();
    }

    public List<String> getQuarantineLogStream() {
        synchronized (quarantineLogStream) {
            return new ArrayList<>(quarantineLogStream);
        }
    }

    @Override
    public void onTransactionProcessed(Transaction transaction, FraudResult fraudResult) {
        recordEvent(transaction, fraudResult);
    }

    @Override
    public void onAccountBalanceChanged(Account account) {}

    @Override
    public void onDeadlockDetected(String details) {
        addQuarantineLog(String.format("[%tT] ALERT: %s", System.currentTimeMillis(), details));
    }

    public void resetTelemetry() {
        totalTransactionsProcessed.set(0);
        flaggedCount.set(0);
        quarantinedCount.set(0);
        timestampQueue.clear();
        synchronized (quarantineLogStream) {
            quarantineLogStream.clear();
        }
    }
}
