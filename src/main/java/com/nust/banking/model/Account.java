package com.nust.banking.model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Abstract base class representing a bank account entity.
 * Enforces encapsulation, thread-safe memory visibility via volatile balance,
 * explicit ReentrantLock synchronization, and Serializable compliance for state snapshotting.
 */
public abstract class Account implements Comparable<Account>, Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String ownerName;
    private volatile double balance;
    private transient ReentrantLock lock;

    public Account(String id, String ownerName, double initialBalance) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Account ID cannot be null or blank");
        }
        if (ownerName == null || ownerName.isBlank()) {
            throw new IllegalArgumentException("Owner name cannot be null or blank");
        }
        if (initialBalance < 0.0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }
        this.id = id;
        this.ownerName = ownerName;
        this.balance = initialBalance;
        this.lock = new ReentrantLock(true); // Fair lock policy to prevent thread starvation
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.lock = new ReentrantLock(true); // Re-initialize lock instance upon deserialization
    }

    public String getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    /**
     * Unsynchronized point-in-time read of the balance.
     * Volatile keyword guarantees memory visibility across threads.
     */
    public double getBalance() {
        return balance;
    }

    public ReentrantLock getLock() {
        if (lock == null) {
            lock = new ReentrantLock(true);
        }
        return lock;
    }

    /**
     * Credits funds into the account. Must be invoked inside a lock boundary.
     */
    public void credit(double amount) {
        if (amount <= 0.0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.balance += amount;
    }

    /**
     * Debits funds from the account. Abstract method implemented by subclasses
     * to enforce specific withdrawal and overdraft policies.
     */
    public abstract void debit(double amount);

    /**
     * Protected setter for subclasses handling special balance adjustments.
     */
    protected void setBalanceDirectly(double newBalance) {
        this.balance = newBalance;
    }

    /**
     * Deterministic natural ordering by Account ID to prevent circular wait deadlocks.
     */
    @Override
    public int compareTo(Account other) {
        return this.id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return Objects.equals(id, account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("%s[ID=%s, Owner='%s', Balance=RS %.2f]",
                getClass().getSimpleName(), id, ownerName, balance);
    }
}
