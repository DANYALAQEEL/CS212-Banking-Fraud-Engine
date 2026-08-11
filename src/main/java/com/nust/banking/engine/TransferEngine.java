package com.nust.banking.engine;

import com.nust.banking.model.Account;
import com.nust.banking.model.Transaction;
import com.nust.banking.model.Transaction.TransactionStatus;

/**
 * Core transaction execution engine that executes atomic multi-account transfers.
 * Uses LockStrategy to enforce lock ordering, routes transaction fees to fee accounts,
 * and performs atomic TOCTOU (Time-of-Check to Time-of-Use) re-validation inside lock boundaries.
 */
public class TransferEngine {

    /**
     * Processes an atomic transfer between two accounts using the default SafeLockStrategy.
     */
    public Transaction processTransfer(Account fromAccount, Account toAccount, double amount) {
        return processTransfer(fromAccount, toAccount, null, amount, new LockStrategy.SafeLockStrategy());
    }

    /**
     * Processes an atomic transfer between two accounts using a specified LockStrategy.
     */
    public Transaction processTransfer(Account fromAccount, Account toAccount, double amount, LockStrategy strategy) {
        return processTransfer(fromAccount, toAccount, null, amount, strategy);
    }

    /**
     * Processes an atomic transfer between two accounts routing fees using default SafeLockStrategy.
     */
    public Transaction processTransfer(Account fromAccount, Account toAccount, Account feeAccount, double amount) {
        return processTransfer(fromAccount, toAccount, feeAccount, amount, new LockStrategy.SafeLockStrategy());
    }

    /**
     * Processes an atomic transfer between two accounts safely under lock protection,
     * routing any transaction fee to a designated fee account (e.g. BANK-FEES).
     *
     * @param fromAccount The source account to debit
     * @param toAccount   The destination account to credit
     * @param feeAccount  Optional account to receive transaction fees (can be null)
     * @param amount      The transaction amount
     * @param strategy    LockStrategy to use for locking
     * @return A Transaction record detailing the outcome
     */
    public Transaction processTransfer(Account fromAccount, Account toAccount, Account feeAccount, double amount, LockStrategy strategy) {
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

        final Transaction[] resultHolder = new Transaction[1];
        double fee = fromAccount.feeFor(amount);
        LockStrategy effectiveStrategy = strategy != null ? strategy : new LockStrategy.SafeLockStrategy();

        try {
            Runnable action = () -> {
                // --- ATOMIC TOCTOU RE-VALIDATION INSIDE LOCK BOUNDARY ---
                try {
                    fromAccount.debit(amount);  // Enforces overdraft / fee / credit limits
                    toAccount.credit(amount);  // Credits destination balance
                    if (fee > 0.0 && feeAccount != null) {
                        feeAccount.credit(fee); // Routes transaction fee to institution fee account
                    }
                    resultHolder[0] = tx.withStatus(
                        TransactionStatus.COMPLETED,
                        String.format("Successfully transferred RS %.2f from %s to %s", amount, fromAccount.getId(), toAccount.getId())
                    );
                } catch (IllegalStateException | IllegalArgumentException e) {
                    resultHolder[0] = tx.withStatus(
                        TransactionStatus.FAILED_INSUFFICIENT_FUNDS,
                        "TOCTOU Guard: " + e.getMessage()
                    );
                }
            };

            if (fee > 0.0 && feeAccount != null && !feeAccount.getId().equals(fromAccount.getId()) && !feeAccount.getId().equals(toAccount.getId())) {
                AccountLockManager.executeWithLocks(action, fromAccount, toAccount, feeAccount);
            } else {
                effectiveStrategy.executeWithLocks(fromAccount, toAccount, action);
            }
        } catch (Exception e) {
            return tx.withStatus(TransactionStatus.FAILED_INVALID_ACCOUNT, "Execution error: " + e.getMessage());
        }

        return resultHolder[0] != null ? resultHolder[0] : tx.withStatus(TransactionStatus.FAILED_INVALID_ACCOUNT, "Unknown execution error");
    }
}
