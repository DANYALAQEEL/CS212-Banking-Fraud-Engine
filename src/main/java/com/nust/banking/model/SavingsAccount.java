package com.nust.banking.model;

/**
 * Concrete account subclass representing a Interest-Bearing Savings Account.
 * Enforces a strict minimum balance requirement and daily withdrawal limits.
 */
public class SavingsAccount extends Account {
    private static final long serialVersionUID = 1L;

    private final double interestRate;      // Annual interest rate (e.g. 0.05 = 5%)
    private final double minimumBalance;   // Minimum balance threshold (e.g. RS 1000.0)

    public SavingsAccount(String id, String ownerName, double initialBalance, double interestRate, double minimumBalance) {
        super(id, ownerName, initialBalance);
        if (interestRate < 0.0) {
            throw new IllegalArgumentException("Interest rate cannot be negative");
        }
        if (minimumBalance < 0.0) {
            throw new IllegalArgumentException("Minimum balance cannot be negative");
        }
        if (initialBalance < minimumBalance) {
            throw new IllegalArgumentException("Initial balance cannot be below minimum balance requirement");
        }
        this.interestRate = interestRate;
        this.minimumBalance = minimumBalance;
    }

    public double getInterestRate() {
        return interestRate;
    }

    public double getMinimumBalance() {
        return minimumBalance;
    }

    /**
     * Applies annual interest to the current balance.
     * Must be invoked inside a lock boundary.
     */
    public void applyInterest() {
        double interest = getBalance() * interestRate;
        credit(interest);
    }

    /**
     * Applies monthly interest (annual interest / 12) to the current balance.
     */
    public void applyMonthlyInterest() {
        double monthlyInterest = getBalance() * (interestRate / 12.0);
        credit(monthlyInterest);
    }

    @Override
    public void debit(double amount) {
        if (amount <= 0.0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (getBalance() - amount < minimumBalance) {
            throw new IllegalStateException(String.format(
                "Savings withdrawal denied for Account %s: Remaining balance RS %.2f would drop below minimum balance requirement of RS %.2f",
                getId(), getBalance() - amount, minimumBalance
            ));
        }
        setBalanceDirectly(getBalance() - amount);
    }
}
