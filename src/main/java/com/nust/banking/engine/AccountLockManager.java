package com.nust.banking.engine;

import com.nust.banking.model.Account;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe Lock Manager enforcing deterministic lock ordering on an arbitrary number of Account objects.
 * Sorts account locks using the natural lexicographical ordering of Account IDs
 * to break Coffman's Circular Wait condition and guarantee zero deadlocks
 * during concurrent multi-account transfers and multi-account balance mutations.
 */
public class AccountLockManager {

    /**
     * Executes a critical section under deterministic lock ordering across two accounts.
     * Delegates to the general N-account lock execution engine.
     */
    public static void executeWithLocks(Account acc1, Account acc2, Runnable action) {
        executeWithLocks(action, acc1, acc2);
    }

    /**
     * Executes a critical section holding locks on all supplied accounts.
     * Accounts are sorted by natural ID order before acquisition, so any two callers
     * requesting any overlapping set acquire the shared locks in the same relative order.
     * This breaks Coffman's circular-wait condition for arbitrary account counts.
     *
     * @param action   The critical section to execute under lock protection
     * @param accounts Array of accounts to lock
     * @throws IllegalArgumentException if accounts array is null/empty, any account is null, or duplicate IDs are present
     */
    public static void executeWithLocks(Runnable action, Account... accounts) {
        if (action == null) {
            throw new IllegalArgumentException("Action runnable cannot be null");
        }
        if (accounts == null || accounts.length == 0) {
            throw new IllegalArgumentException("Accounts array cannot be null or empty for locked execution");
        }

        Set<String> seenIds = new HashSet<>();
        for (Account acc : accounts) {
            if (acc == null) {
                throw new IllegalArgumentException("Account instances cannot be null for locked execution");
            }
            if (!seenIds.add(acc.getId())) {
                throw new IllegalArgumentException(String.format(
                    "Duplicate Account ID %s rejected to prevent circular wait deadlocks and hold-count leaks",
                    acc.getId()
                ));
            }
        }

        // Sort accounts by natural ID comparison to enforce global total ordering
        Account[] sortedAccounts = accounts.clone();
        Arrays.sort(sortedAccounts, Comparator.comparing(Account::getId));

        // Acquire locks in strict ascending ID order
        acquireLocksAndRun(sortedAccounts, 0, action);
    }

    private static void acquireLocksAndRun(Account[] sortedAccounts, int index, Runnable action) {
        if (index >= sortedAccounts.length) {
            action.run();
            return;
        }
        ReentrantLock lock = sortedAccounts[index].getLock();
        lock.lock();
        try {
            acquireLocksAndRun(sortedAccounts, index + 1, action);
        } finally {
            lock.unlock();
        }
    }
}
