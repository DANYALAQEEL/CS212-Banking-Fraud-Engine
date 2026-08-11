package com.nust.banking.engine;

import com.nust.banking.model.*;
import com.nust.banking.persistence.LedgerFileManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Settlement Service & Fraud Integration Test Suite")
class SettlementServiceTest {

    private TransferEngine transferEngine;
    private FraudDetectionEngine fraudEngine;
    private SettlementService settlementService;

    @BeforeEach
    void setUp() {
        transferEngine = new TransferEngine();
        fraudEngine = new FraudDetectionEngine();
        fraudEngine.resetWindowHistory();
        settlementService = new SettlementService(transferEngine, fraudEngine);
    }

    @Test
    @DisplayName("F1: Quarantined transaction (score >= 70) blocks transfer and leaves balances untouched")
    void quarantinedTransactionDoesNotMoveMoney() {
        SavingsAccount src = new SavingsAccount("ACC-001", "Alice", 100000.0, 0.05, 1000.0);
        SavingsAccount dst = new SavingsAccount("ACC-002", "Bob", 50000.0, 0.05, 1000.0);

        for (int i = 0; i < 5; i++) {
            fraudEngine.evaluateTransaction(Transaction.createNew("ACC-001", "ACC-002", 10.0), src);
        }

        SettlementService.SettlementOutcome outcome = settlementService.settle(
            src, dst, 100000.0, new LockStrategy.SafeLockStrategy()
        );

        assertEquals(Transaction.TransactionStatus.FLAGGED_FRAUD, outcome.transaction().status());
        assertEquals(FraudResult.FraudStatus.QUARANTINED, outcome.fraudResult().status());
        assertEquals(100000.0, src.getBalance(), "Source balance must remain untouched");
        assertEquals(50000.0, dst.getBalance(), "Destination balance must remain untouched");
    }

    @Test
    @DisplayName("F1: Flagged suspicious transaction (40 <= score < 70) settles COMPLETED but records audit verdict")
    void flaggedTransactionStillSettlesButIsRecorded() {
        SavingsAccount src = new SavingsAccount("ACC-001", "Alice", 500000.0, 0.05, 1000.0);
        SavingsAccount dst = new SavingsAccount("ACC-002", "Bob", 50000.0, 0.05, 1000.0);

        SettlementService.SettlementOutcome outcome = settlementService.settle(
            src, dst, 150000.0, new LockStrategy.SafeLockStrategy()
        );

        assertEquals(Transaction.TransactionStatus.COMPLETED, outcome.transaction().status());
        assertEquals(FraudResult.FraudStatus.FLAGGED_SUSPICIOUS, outcome.fraudResult().status());
        assertEquals(350000.0, src.getBalance());
        assertEquals(200000.0, dst.getBalance());
    }

    @Test
    @DisplayName("F2: Rapid drain rule fires only when transfer ratio is >= 90%")
    void rapidDrainFiresOnlyAtNinetyPercent() {
        double[] percentages = {30.0, 45.0, 48.0, 60.0, 89.0, 90.0, 95.0};
        for (double percentage : percentages) {
            double initialBalance = 100000.0;
            double amount = initialBalance * (percentage / 100.0);
            SavingsAccount src = new SavingsAccount("ACC-001", "Alice", initialBalance, 0.05, 1000.0);
            Transaction tx = Transaction.createNew("ACC-001", "ACC-002", amount);

            FraudResult verdict = fraudEngine.evaluateTransaction(tx, src, initialBalance);

            if (percentage >= 90.0) {
                assertTrue(verdict.ruleTriggered().contains("RAPID_DRAIN"), "Rapid drain should fire at " + percentage + "%");
            } else {
                assertFalse(verdict.ruleTriggered().contains("RAPID_DRAIN"), "Rapid drain should NOT fire at " + percentage + "%");
            }
        }
    }

    @Test
    @DisplayName("F3: Payment into CreditAccount reduces debt balance and increases available credit")
    void paymentIntoCreditAccountReducesDebt() {
        CreditAccount card = new CreditAccount("ACC-003", "Charlie", 15000.0, 50000.0, 0.18);
        SavingsAccount bank = new SavingsAccount("ACC-001", "Bank", 500000.0, 0.05, 1000.0);

        transferEngine.processTransfer(bank, card, 10000.0);

        assertEquals(5000.0, card.getBalance(), "Debt should decrease to 5,000");
        assertEquals(45000.0, card.getAvailableCredit(), "Available credit should increase to 45,000");
        assertEquals(-5000.0, card.getNetPosition(), "Net position contribution should be -5000");
    }

    @Test
    @DisplayName("F3: Overpayment into CreditAccount clamps debt at zero")
    void overpaymentIntoCreditAccountClampsAtZero() {
        CreditAccount card = new CreditAccount("ACC-003", "Charlie", 5000.0, 50000.0, 0.18);
        card.credit(10000.0);

        assertEquals(0.0, card.getBalance(), "Debt balance should clamp at zero");
        assertEquals(50000.0, card.getAvailableCredit(), "Available credit should equal credit limit");
    }

    @Test
    @DisplayName("F4: CSV round-trip preserves overdrawn CheckingAccount negative balance")
    void csvRoundTripPreservesOverdrawnCheckingAccount() throws Exception {
        CheckingAccount overdrawn = new CheckingAccount("ACC-002", "Bob", 1000.0, 5000.0, 25.0);
        overdrawn.debit(3000.0); // Balance = 1000 - (3000 + 25) = -2025.0

        File tempFile = Files.createTempFile("ledger_test", ".csv").toFile();
        tempFile.deleteOnExit();

        LedgerFileManager.exportAccountsToCsv(List.of(overdrawn), tempFile);
        List<Account> imported = LedgerFileManager.importAccountsFromCsv(tempFile);

        assertEquals(1, imported.size());
        assertEquals(-2025.0, imported.get(0).getBalance(), 1e-2, "Negative balance must be preserved on CSV import");
    }

    @Test
    @DisplayName("F5: CSV round-trip preserves comma in owner name")
    void csvRoundTripPreservesCommaInOwnerName() throws Exception {
        SavingsAccount acc = new SavingsAccount("ACC-001", "Smith, Alice", 50000.0, 0.05, 1000.0);

        File tempFile = Files.createTempFile("ledger_comma", ".csv").toFile();
        tempFile.deleteOnExit();

        LedgerFileManager.exportAccountsToCsv(List.of(acc), tempFile);
        List<Account> imported = LedgerFileManager.importAccountsFromCsv(tempFile);

        assertEquals(1, imported.size());
        assertEquals("Smith, Alice", imported.get(0).getOwnerName());
        assertEquals(50000.0, imported.get(0).getBalance());
    }

    @Test
    @DisplayName("F16: JSON export escapes quotes and control characters")
    void jsonExportEscapesControlCharacters() throws Exception {
        Transaction tx = Transaction.createNew("ACC-001", "ACC-002", 500.0)
                .withStatus(Transaction.TransactionStatus.FAILED_INSUFFICIENT_FUNDS, "Error: \"Denied\"\nLine 2\tTabbed");

        File tempFile = Files.createTempFile("audit_test", ".json").toFile();
        tempFile.deleteOnExit();

        LedgerFileManager.exportTransactionsToJson(List.of(tx), tempFile);
        String jsonContent = Files.readString(tempFile.toPath());

        assertTrue(jsonContent.contains("\\\"Denied\\\""));
        assertTrue(jsonContent.contains("\\nLine 2"));
        assertTrue(jsonContent.contains("\\tTabbed"));
    }

    @Test
    @DisplayName("F9: Institutional net position is conserved across transfers including fees")
    void netPositionIsConservedAcrossTransfers() {
        CheckingAccount chk = new CheckingAccount("ACC-002", "Bob", 100000.0, 5000.0, 25.0);
        SavingsAccount sav = new SavingsAccount("ACC-001", "Alice", 100000.0, 0.05, 1000.0);
        CheckingAccount feeAcc = new CheckingAccount("BANK-FEES", "Fees", 0.0, 0.0, 0.0);

        double initialTotal = chk.getNetPosition() + sav.getNetPosition() + feeAcc.getNetPosition();

        transferEngine.processTransfer(chk, sav, feeAcc, 1000.0);

        double finalTotal = chk.getNetPosition() + sav.getNetPosition() + feeAcc.getNetPosition();

        assertEquals(initialTotal, finalTotal, 1e-6, "Total institutional net position must remain invariant");
        assertEquals(25.0, feeAcc.getBalance(), "Fee account must receive transaction fee");
    }

    @Test
    @DisplayName("F14: Velocity window tracking is exact under concurrent transfers")
    void velocityWindowIsExactUnderConcurrency() throws Exception {
        int threads = 10;
        int txPerThread = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    for (int j = 0; j < txPerThread; j++) {
                        fraudEngine.evaluateTransaction(Transaction.createNew("ACC-001", "ACC-002", 10.0), null);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        Transaction lastTx = Transaction.createNew("ACC-001", "ACC-002", 10.0);
        FraudResult result = fraudEngine.evaluateTransaction(lastTx, null);

        assertTrue(result.ruleTriggered().contains("HIGH_VELOCITY"));
    }

    @Test
    @DisplayName("D1: TransactionPipelineQueue enforces bounded backpressure")
    void pipelineQueueAppliesBackpressure() throws Exception {
        TransactionPipelineQueue queue = new TransactionPipelineQueue(10);

        for (int i = 0; i < 10; i++) {
            boolean enqueued = queue.offer(Transaction.createNew("ACC-001", "ACC-002", 100.0), 10, TimeUnit.MILLISECONDS);
            assertTrue(enqueued, "Initial items should fit in queue");
        }

        boolean rejected = queue.offer(Transaction.createNew("ACC-001", "ACC-002", 100.0), 10, TimeUnit.MILLISECONDS);
        assertFalse(rejected, "Overflow item must be rejected when queue is full");
    }
}
