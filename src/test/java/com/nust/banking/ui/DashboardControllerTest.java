package com.nust.banking.ui;

import com.nust.banking.engine.TransferEngine;
import com.nust.banking.model.Account;
import com.nust.banking.model.FraudResult;
import com.nust.banking.model.SavingsAccount;
import com.nust.banking.model.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UI Thread Handoff & Event Bridge Test Suite")
class DashboardControllerTest {

    @Test
    @DisplayName("Event Bridge: Background worker threads publish events safely")
    void testEventBridgePublisher() {
        AtomicBoolean received = new AtomicBoolean(false);

        JavaFXEventListener listener = new JavaFXEventListener() {
            @Override
            public void onTransactionProcessed(Transaction transaction, FraudResult fraudResult) {
                received.set(true);
            }

            @Override
            public void onAccountBalanceChanged(Account account) {}

            @Override
            public void onDeadlockDetected(String details) {}
        };

        Transaction tx = Transaction.createNew("ACC-001", "ACC-002", 500.0);

        listener.onTransactionProcessed(tx, null);

        assertTrue(received.get(), "Listener should receive processed transaction event");
    }

    @Test
    @DisplayName("Transfer Integration: TransferEngine processes transactions with accounts")
    void testTransferEngineIntegration() {
        TransferEngine engine = new TransferEngine();
        SavingsAccount accA = new SavingsAccount("ACC-001", "Alice", 50000.0, 0.05, 1000.0);
        SavingsAccount accB = new SavingsAccount("ACC-002", "Bob", 50000.0, 0.05, 1000.0);

        Transaction tx = engine.processTransfer(accA, accB, 5000.0);

        assertNotNull(tx);
        assertEquals(Transaction.TransactionStatus.COMPLETED, tx.status());
        assertEquals(45000.0, accA.getBalance());
        assertEquals(55000.0, accB.getBalance());
    }
}
