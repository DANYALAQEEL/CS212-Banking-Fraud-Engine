package com.nust.banking.engine;

import com.nust.banking.model.Account;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe Lock Manager enforcing deterministic lock ordering on Account pairs.
 * Sorts account locks using the natural lexicographical ordering of Account IDs
 * to break Coffman's Circular Wait condition and guarantee zero deadlocks
 * during concurrent multi-account transfers.
 */
public class AccountLockManager {

    /**
     * Executes a critical section under deterministic lock ordering.
     *
     * @param acc1   The first account in the transfer
     * @param acc2   The second account in the transfer
     * @param action The critical section to execute under lock protection
     * @throws IllegalArgumentException if acc1 and acc2 are the same account instance or have identical IDs
     */
    public static void executeWithLocks(Account acc1, Account acc2, Runnable action) {
        if (acc1 == null || acc2 == null) {
            throw new IllegalArgumentException("Account instances cannot be null for locked execution");
        }
        if (acc1.getId().equals(acc2.getId())) {
            throw new IllegalArgumentException(String.format(
                "Self-transfer rejected for Account %s: Account IDs must be distinct to prevent hold-count leaks and logic corruption",
                acc1.getId()
            ));
        }

        // Deterministic Lock Ordering: Sort accounts using natural compareTo() order
        Account firstLockAccount = acc1.compareTo(acc2) < 0 ? acc1 : acc2;
        Account secondLockAccount = firstLockAccount == acc1 ? acc2 : acc1;

        ReentrantLock firstLock = firstLockAccount.getLock();
        ReentrantLock secondLock = secondLockAccount.getLock();

        // Acquire locks in strict ascending ID order
        firstLock.lock();
        try {
            secondLock.lock();
            try {
                // Execute critical section inside lock boundary
                action.run();
            } finally {
                secondLock.unlock();
            }
        } finally {
            firstLock.unlock();
        }
    }
}
