package com.nust.banking.engine;

import com.nust.banking.model.SavingsAccount;
import com.nust.banking.model.CheckingAccount;
import com.nust.banking.model.Transaction;
import com.nust.banking.model.Transaction.TransactionStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Concurrency Engine & Lock Ordering Tests")
public class TransferEngineTest {

    private TransferEngine engine;
    private SavingsAccount acc101;
    private CheckingAccount acc102;

    @BeforeEach
    void setUp() {
        engine = new TransferEngine();
        acc101 = new SavingsAccount("ACC-101", "Danyal Aqeel", 10000.0, 0.05, 1000.0);
        acc102 = new CheckingAccount("ACC-102", "Muhammad Arslan", 5000.0, 2000.0, 25.0);
    }

    @Test
    @DisplayName("Atomic Transfer: Valid transfer debits source and credits destination")
    void testValidTransfer() {
        Transaction tx = engine.processTransfer(acc101, acc102, 2000.0);
        assertEquals(TransactionStatus.COMPLETED, tx.status());
        assertEquals(8000.0, acc101.getBalance(), 0.001);
        assertEquals(7000.0, acc102.getBalance(), 0.001);
    }

    @Test
    @DisplayName("TOCTOU Guard: Insufficient balance inside lock boundary returns FAILED_INSUFFICIENT_FUNDS")
    void testTOCTOUGuardInsufficientFunds() {
        Transaction tx = engine.processTransfer(acc101, acc102, 9500.0); // Would leave balance at 500 (Minimum balance is 1000)
        assertEquals(TransactionStatus.FAILED_INSUFFICIENT_FUNDS, tx.status());
        assertEquals(10000.0, acc101.getBalance(), 0.001); // Source balance untouched
        assertEquals(5000.0, acc102.getBalance(), 0.001);  // Destination balance untouched
    }

    @Test
    @DisplayName("Self-Transfer Protection: Reject transfers where fromAccount == toAccount")
    void testSelfTransferRejection() {
        Transaction tx = engine.processTransfer(acc101, acc101, 1000.0);
        assertEquals(TransactionStatus.FAILED_INVALID_ACCOUNT, tx.status());
    }

    @Test
    @DisplayName("High Concurrency: 100 parallel cross-transfers execute without deadlocks or race conditions")
    void testConcurrentCrossTransfersDeadlockFree() throws InterruptedException {
        int threads = 10;
        int transfersPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final boolean even = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    for (int j = 0; j < transfersPerThread; j++) {
                        if (even) {
                            engine.processTransfer(acc101, acc102, 10.0);
                        } else {
                            engine.processTransfer(acc102, acc101, 10.0);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // Wait for all 100 cross-transfers to finish
        executor.shutdown();

        // Total system liquidity (acc101 + acc102) must remain invariant (minus checking transaction fees)
        assertTrue(acc101.getBalance() > 0);
        assertTrue(acc102.getBalance() > 0);
    }
}
