package com.nust.banking.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable value object representing a financial transfer transaction.
 * Being immutable and Serializable, instances of Transaction can be published across background
 * worker threads, JavaFX UI thread, and binary snapshot streams safely.
 */
public record Transaction(
    String transactionId,
    String fromAccountId,
    String toAccountId,
    double amount,
    Instant timestamp,
    TransactionStatus status,
    String statusDetails
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum TransactionStatus {
        PENDING_PIPELINE,
        FLAGGED_FRAUD,
        COMPLETED,
        FAILED_INSUFFICIENT_FUNDS,
        FAILED_INVALID_ACCOUNT,
        REJECTED_TOCTOU
    }

    public Transaction {
        if (transactionId == null || transactionId.isBlank()) {
            transactionId = UUID.randomUUID().toString().substring(0, 8);
        }
        if (fromAccountId == null || fromAccountId.isBlank()) {
            throw new IllegalArgumentException("From Account ID cannot be null or blank");
        }
        if (toAccountId == null || toAccountId.isBlank()) {
            throw new IllegalArgumentException("To Account ID cannot be null or blank");
        }
        if (amount <= 0.0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (status == null) {
            status = TransactionStatus.PENDING_PIPELINE;
        }
    }

    public static Transaction createNew(String fromAccountId, String toAccountId, double amount) {
        return new Transaction(
            UUID.randomUUID().toString().substring(0, 8),
            fromAccountId,
            toAccountId,
            amount,
            Instant.now(),
            TransactionStatus.PENDING_PIPELINE,
            "Submitted to pipeline"
        );
    }

    public Transaction withStatus(TransactionStatus newStatus, String details) {
        return new Transaction(
            this.transactionId,
            this.fromAccountId,
            this.toAccountId,
            this.amount,
            this.timestamp,
            newStatus,
            details
        );
    }
}
