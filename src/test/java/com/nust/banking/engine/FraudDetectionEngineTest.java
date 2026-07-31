package com.nust.banking.engine;

import com.nust.banking.model.FraudResult;
import com.nust.banking.model.SavingsAccount;
import com.nust.banking.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FraudDetectionEngine Test Suite")
class FraudDetectionEngineTest {

    private FraudDetectionEngine fraudEngine;

    @BeforeEach
    void setUp() {
        fraudEngine = new FraudDetectionEngine();
        fraudEngine.resetWindowHistory();
    }

    @Test
    @DisplayName("Normal Transaction: Low amount passes cleanly with CLEARED status")
    void testNormalTransactionCleared() {
        SavingsAccount acc1 = new SavingsAccount("ACC-001", "Alice", 10000.0, 0.05, 1000.0);
        Transaction tx = Transaction.createNew("ACC-001", "ACC-002", 500.0);

        FraudResult result = fraudEngine.evaluateTransaction(tx, acc1);

        assertEquals(0.0, result.riskScore());
        assertEquals(FraudResult.FraudStatus.CLEARED, result.status());
        assertFalse(result.isActionable());
    }

    @Test
    @DisplayName("Large Amount Rule: RS 150,000 transaction triggers FLAGGED_SUSPICIOUS status")
    void testLargeAmountRule() {
        SavingsAccount acc1 = new SavingsAccount("ACC-001", "Alice", 500000.0, 0.05, 1000.0);
        Transaction tx = Transaction.createNew("ACC-001", "ACC-002", 150000.0);

        FraudResult result = fraudEngine.evaluateTransaction(tx, acc1);

        assertEquals(40.0, result.riskScore());
        assertEquals(FraudResult.FraudStatus.FLAGGED_SUSPICIOUS, result.status());
        assertTrue(result.ruleTriggered().contains("LARGE_AMOUNT"));
        assertTrue(result.isActionable());
    }

    @Test
    @DisplayName("High Velocity Rule: > 5 transfers within 60s triggers high velocity penalty")
    void testHighVelocityRule() {
        SavingsAccount acc1 = new SavingsAccount("ACC-001", "Alice", 50000.0, 0.05, 1000.0);

        for (int i = 0; i < 5; i++) {
            Transaction tx = Transaction.createNew("ACC-001", "ACC-002", 100.0);
            fraudEngine.evaluateTransaction(tx, acc1);
        }

        Transaction tx6 = Transaction.createNew("ACC-001", "ACC-002", 100.0);
        FraudResult result = fraudEngine.evaluateTransaction(tx6, acc1);

        assertEquals(35.0, result.riskScore());
        assertEquals(FraudResult.FraudStatus.CLEARED, result.status());
        assertTrue(result.ruleTriggered().contains("HIGH_VELOCITY"));
    }

    @Test
    @DisplayName("Suspicious Target Rule: > 3 distinct sources funding same target within 60s")
    void testSuspiciousTargetRule() {
        SavingsAccount accDest = new SavingsAccount("ACC-DEST", "Destination", 50000.0, 0.05, 1000.0);

        for (int i = 1; i <= 3; i++) {
            SavingsAccount src = new SavingsAccount("SRC-00" + i, "Sender " + i, 20000.0, 0.05, 1000.0);
            fraudEngine.evaluateTransaction(Transaction.createNew(src.getId(), accDest.getId(), 500.0), src);
        }

        SavingsAccount src4 = new SavingsAccount("SRC-004", "Sender 4", 20000.0, 0.05, 1000.0);
        FraudResult result = fraudEngine.evaluateTransaction(Transaction.createNew(src4.getId(), accDest.getId(), 500.0), src4);

        assertEquals(30.0, result.riskScore());
        assertEquals(FraudResult.FraudStatus.CLEARED, result.status());
        assertTrue(result.ruleTriggered().contains("SUSPICIOUS_TARGET"));
    }

    @Test
    @DisplayName("Rapid Drain Rule Isolated: Transfer of 95% balance (below 100k) triggers Rapid Drain")
    void testRapidDrainRuleIsolated() {
        SavingsAccount acc1 = new SavingsAccount("ACC-001", "Alice", 10000.0, 0.05, 500.0);
        Transaction tx = Transaction.createNew("ACC-001", "ACC-002", 9500.0);

        FraudResult result = fraudEngine.evaluateTransaction(tx, acc1);

        assertEquals(25.0, result.riskScore());
        assertEquals(FraudResult.FraudStatus.CLEARED, result.status());
        assertTrue(result.ruleTriggered().contains("RAPID_DRAIN"));
        assertFalse(result.ruleTriggered().contains("LARGE_AMOUNT"));
    }

    @Test
    @DisplayName("Null Guard: Null transaction throws NullPointerException")
    void testNullTransactionRejected() {
        SavingsAccount acc1 = new SavingsAccount("ACC-001", "Alice", 10000.0, 0.05, 1000.0);
        assertThrows(NullPointerException.class, () -> fraudEngine.evaluateTransaction(null, acc1));
    }

    @Test
    @DisplayName("Risk Score Cap: Score is capped at 100.0 even when raw score exceeds 100")
    void testRiskScoreCappedAtOneHundred() {
        SavingsAccount acc1 = new SavingsAccount("ACC-001", "Alice", 120000.0, 0.05, 1000.0);

        for (int i = 0; i < 5; i++) {
            fraudEngine.evaluateTransaction(Transaction.createNew("ACC-001", "ACC-DEST", 100.0), acc1);
        }

        for (int s = 2; s <= 4; s++) {
            SavingsAccount src = new SavingsAccount("SRC-00" + s, "Source " + s, 20000.0, 0.05, 1000.0);
            fraudEngine.evaluateTransaction(Transaction.createNew(src.getId(), "ACC-DEST", 100.0), src);
        }

        Transaction tx = Transaction.createNew("ACC-001", "ACC-DEST", 115000.0);
        FraudResult result = fraudEngine.evaluateTransaction(tx, acc1);

        assertEquals(100.0, result.riskScore());
        assertEquals(FraudResult.FraudStatus.QUARANTINED, result.status());
        assertTrue(result.isActionable());
    }

    @Test
    @DisplayName("Concurrent Multi-Threaded Fraud Evaluation Safety")
    void testConcurrentFraudEvaluation() throws InterruptedException {
        int threadCount = 10;
        int txPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalEvaluated = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    SavingsAccount acc = new SavingsAccount("ACC-THREAD-" + threadId, "Thread User", 100000.0, 0.05, 1000.0);
                    for (int i = 0; i < txPerThread; i++) {
                        Transaction tx = Transaction.createNew("ACC-THREAD-" + threadId, "ACC-DEST", 50.0);
                        FraudResult res = fraudEngine.evaluateTransaction(tx, acc);
                        assertNotNull(res);
                        totalEvaluated.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "All multi-threaded fraud evaluations should complete cleanly");
        assertEquals(threadCount * txPerThread, totalEvaluated.get());

        executor.shutdown();
    }
}
