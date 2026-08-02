package com.nust.banking.ui;

import com.nust.banking.model.FraudResult;
import com.nust.banking.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Telemetry & Security Visual Metrics Test Suite")
class TelemetryViewControllerTest {

    private TelemetryViewController controller;

    @BeforeEach
    void setUp() {
        controller = new TelemetryViewController();
    }

    @Test
    @DisplayName("Telemetry Aggregation: Initial state starts at zero risk")
    void testInitialState() {
        assertEquals(0, controller.getTotalTransactionsProcessed());
        assertEquals(0, controller.getFlaggedCount());
        assertEquals(0, controller.getQuarantinedCount());
        assertEquals(0.0, controller.calculateCompositeRiskScore(), 0.01);
        assertEquals("NORMAL / LOW", controller.getThreatLevelBadge());
    }

    @Test
    @DisplayName("Risk Scoring: Flagged and quarantined events elevate composite threat badge")
    void testRiskScoreElevation() {
        Transaction tx1 = Transaction.createNew("ACC-001", "ACC-002", 500.0);
        Transaction tx2 = Transaction.createNew("ACC-001", "ACC-002", 150000.0);

        FraudResult cleared = FraudResult.cleared(tx1.transactionId());
        FraudResult quarantined = new FraudResult(tx2.transactionId(), FraudResult.FraudStatus.QUARANTINED, "Large Amount Rule Triggered", 85.0, Instant.now());

        controller.recordEvent(tx1, cleared);
        controller.recordEvent(tx2, quarantined);

        assertEquals(2, controller.getTotalTransactionsProcessed());
        assertEquals(1, controller.getQuarantinedCount());
        assertTrue(controller.calculateCompositeRiskScore() > 0.0);
        assertFalse(controller.getQuarantineLogStream().isEmpty());
    }

    @Test
    @DisplayName("TPS Throughput: Calculates sliding-window transactions per second")
    void testTpsCalculation() {
        Transaction tx = Transaction.createNew("ACC-001", "ACC-002", 100.0);

        for (int i = 0; i < 10; i++) {
            controller.recordEvent(tx, FraudResult.cleared(tx.transactionId()));
        }

        double tps = controller.calculateCurrentTps(1000);
        assertTrue(tps >= 10.0, "TPS should calculate throughput over sliding window");
    }

    @Test
    @DisplayName("Reset Telemetry: Clears queues and resets counts to zero")
    void testResetTelemetry() {
        Transaction tx = Transaction.createNew("ACC-001", "ACC-002", 100.0);
        controller.recordEvent(tx, FraudResult.cleared(tx.transactionId()));

        controller.resetTelemetry();

        assertEquals(0, controller.getTotalTransactionsProcessed());
        assertEquals(0.0, controller.calculateCompositeRiskScore(), 0.01);
        assertTrue(controller.getQuarantineLogStream().isEmpty());
    }
}
