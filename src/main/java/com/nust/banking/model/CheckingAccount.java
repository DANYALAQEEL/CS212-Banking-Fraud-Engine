package com.nust.banking.model;

/**
 * Concrete account subclass representing a Checking / Current Account.
 * Allows overdraft protection down to a specified credit limit and applies
 * transactional processing fees.
 */
public class CheckingAccount extends Account {
    private final double overdraftLimit;   // Max negative balance allowed (e.g. 5000.0)
    private final double transactionFee;   // Fee per debit transaction (e.g. 25.0)

    public CheckingAccount(String id, String ownerName, double initialBalance, double overdraftLimit, double transactionFee) {
        super(id, ownerName, initialBalance);
        if (overdraftLimit < 0.0) {
            throw new IllegalArgumentException("Overdraft limit cannot be negative");
        }
        if (transactionFee < 0.0) {
            throw new IllegalArgumentException("Transaction fee cannot be negative");
        }
        this.overdraftLimit = overdraftLimit;
        this.transactionFee = transactionFee;
    }

    public double getOverdraftLimit() {
        return overdraftLimit;
    }

    public double getTransactionFee() {
        return transactionFee;
    }

    @Override
    public void debit(double amount) {
        if (amount <= 0.0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        double totalDeduction = amount + transactionFee;
        if (getBalance() - totalDeduction < -overdraftLimit) {
            throw new IllegalStateException(String.format(
                "Checking withdrawal denied for Account %s: Total deduction RS %.2f exceeds overdraft limit of RS %.2f (Current balance: RS %.2f)",
                getId(), totalDeduction, overdraftLimit, getBalance()
            ));
        }
        setBalanceDirectly(getBalance() - totalDeduction);
    }
}
