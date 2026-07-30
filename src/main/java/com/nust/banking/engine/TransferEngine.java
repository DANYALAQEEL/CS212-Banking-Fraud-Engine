package com.nust.banking.engine;

import com.nust.banking.model.Account;
import com.nust.banking.model.Transaction;
import com.nust.banking.model.Transaction.TransactionStatus;

/**
 * Core transaction execution engine that executes atomic multi-account transfers.
 * Uses AccountLockManager to enforce lock ordering and performs atomic TOCTOU
 * (Time-of-Check to Time-of-Use) re-validation inside lock boundaries.
 */
public class TransferEngine {

    /**
     * Processes an atomic transfer between two accounts safely under lock protection.
     *
     * @param fromAccount The source account to debit
     * @param toAccount   The destination account to credit
     * @param amount      The transaction amount
     * @return A Transaction record detailing the outcome
     */
    public Transaction processTransfer(Account fromAccount, Account toAccount, double amount) {
        Transaction tx = Transaction.createNew(
            fromAccount != null ? fromAccount.getId() : "UNKNOWN",
            toAccount != null ? toAccount.getId() : "UNKNOWN",
            amount
        );

        if (fromAccount == null || toAccount == null) {
            return tx.withStatus(TransactionStatus.FAILED_INVALID_ACCOUNT, "One or both account instances are null");
        }
        if (fromAccount.getId().equals(toAccount.getId())) {
            return tx.withStatus(TransactionStatus.FAILED_INVALID_ACCOUNT, "Self-transfers are not allowed");
        }
        if (amount <= 0.0) {
            return tx.withStatus(TransactionStatus.FAILED_INVALID_ACCOUNT, "Transfer amount must be positive");
        }

        // Holder for execution status across the Runnable lambda
        final Transaction[] resultHolder = new Transaction[1];

        try {
            AccountLockManager.executeWithLocks(fromAccount, toAccount, () -> {
                // --- ATOMIC TOCTOU RE-VALIDATION INSIDE LOCK BOUNDARY ---
                // Re-check source balance and subclass business rules immediately before state mutation
                try {
                    fromAccount.debit(amount);  // Enforces minimum balance / overdraft / credit limits
                    toAccount.credit(amount);  // Credits destination balance
                    resultHolder[0] = tx.withStatus(
                        TransactionStatus.COMPLETED,
                        String.format("Successfully transferred RS %.2f from %s to %s", amount, fromAccount.getId(), toAccount.getId())
                    );
                } catch (IllegalStateException | IllegalArgumentException e) {
                    // TOCTOU guard caught insufficient funds or rule violation inside lock
                    resultHolder[0] = tx.withStatus(
                        TransactionStatus.FAILED_INSUFFICIENT_FUNDS,
                        "TOCTOU Guard: " + e.getMessage()
                    );
                }
            });
        } catch (Exception e) {
            return tx.withStatus(TransactionStatus.FAILED_INVALID_ACCOUNT, "Execution error: " + e.getMessage());
        }

        return resultHolder[0] != null ? resultHolder[0] : tx.withStatus(TransactionStatus.FAILED_INVALID_ACCOUNT, "Unknown execution error");
    }
}
