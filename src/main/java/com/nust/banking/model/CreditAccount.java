package com.nust.banking.model;

/**
 * Concrete account subclass representing a Revolving Credit Card Account.
 * Balance represents outstanding debt balance. Debits increase debt up to
 * the credit limit, while credits pay down the balance.
 */
public class CreditAccount extends Account {
    private final double creditLimit;      // Maximum credit capacity (e.g. 100000.0)
    private final double apr;              // Annual percentage rate for interest

    public CreditAccount(String id, String ownerName, double initialDebt, double creditLimit, double apr) {
        super(id, ownerName, initialDebt);
        if (creditLimit <= 0.0) {
            throw new IllegalArgumentException("Credit limit must be positive");
        }
        if (apr < 0.0) {
            throw new IllegalArgumentException("APR cannot be negative");
        }
        if (initialDebt > creditLimit) {
            throw new IllegalArgumentException("Initial debt cannot exceed credit limit");
        }
        this.creditLimit = creditLimit;
        this.apr = apr;
    }

    public double getCreditLimit() {
        return creditLimit;
    }

    public double getApr() {
        return apr;
    }

    public double getAvailableCredit() {
        return Math.max(0.0, creditLimit - getBalance());
    }

    @Override
    public void debit(double amount) {
        if (amount <= 0.0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (getBalance() + amount > creditLimit) {
            throw new IllegalStateException(String.format(
                "Credit charge denied for Account %s: New debt RS %.2f exceeds credit limit of RS %.2f (Available credit: RS %.2f)",
                getId(), getBalance() + amount, creditLimit, getAvailableCredit()
            ));
        }
        setBalanceDirectly(getBalance() + amount);
    }
}
