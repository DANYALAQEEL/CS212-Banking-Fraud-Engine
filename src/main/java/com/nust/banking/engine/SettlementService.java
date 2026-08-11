package com.nust.banking.engine;

import com.nust.banking.model.Account;
import com.nust.banking.model.FraudResult;
import com.nust.banking.model.Transaction;
import com.nust.banking.model.Transaction.TransactionStatus;

/**
 * Owns the canonical settlement ordering: evaluate fraud on the pre-transfer state,
 * then either block the transaction or execute it. The controller must not call
 * TransferEngine directly.
 */
public class SettlementService {
    private final TransferEngine transferEngine;
    private final FraudDetectionEngine fraudEngine;

    public record SettlementOutcome(Transaction transaction, FraudResult fraudResult) {}

    public SettlementService(TransferEngine transferEngine, FraudDetectionEngine fraudEngine) {
        this.transferEngine = transferEngine != null ? transferEngine : new TransferEngine();
        this.fraudEngine = fraudEngine != null ? fraudEngine : new FraudDetectionEngine();
    }

    public SettlementOutcome settle(Account from, Account to, double amount, LockStrategy strategy) {
        return settle(from, to, null, amount, strategy);
    }

    public SettlementOutcome settle(Account from, Account to, Account feeAccount, double amount, LockStrategy strategy) {
        Transaction pending = Transaction.createNew(
            from != null ? from.getId() : "UNKNOWN",
            to != null ? to.getId() : "UNKNOWN",
            amount
        );

        if (from == null || to == null) {
            Transaction failed = pending.withStatus(TransactionStatus.FAILED_INVALID_ACCOUNT, "One or both account instances are null");
            return new SettlementOutcome(failed, FraudResult.cleared(pending.transactionId()));
        }

        // Fraud runs against the PRE-transfer balance
        FraudResult verdict = fraudEngine.evaluateTransaction(pending, from);

        if (verdict.status() == FraudResult.FraudStatus.QUARANTINED) {
            Transaction blocked = pending.withStatus(
                TransactionStatus.FLAGGED_FRAUD,
                "BLOCKED by fraud engine (score " + verdict.riskScore() + "): " + verdict.ruleTriggered()
            );
            return new SettlementOutcome(blocked, verdict);
        }

        // FLAGGED_SUSPICIOUS settles but is marked for audit; CLEARED settles normally.
        Transaction settled = transferEngine.processTransfer(from, to, feeAccount, amount, strategy);
        return new SettlementOutcome(settled, verdict);
    }
}
