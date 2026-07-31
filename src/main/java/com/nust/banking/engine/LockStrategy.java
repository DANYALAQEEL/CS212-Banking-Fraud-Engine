package com.nust.banking.engine;

import com.nust.banking.model.Account;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Strategy interface and concrete implementations for dual-account locking strategies.
 *
 * <p>Demonstrates lock contention management, deadlock simulation, and recovery mechanisms:
 * <ul>
 *   <li><b>SafeLockStrategy:</b> Enforces total lexicographical lock ordering via {@link AccountLockManager} (0 deadlocks).</li>
 *   <li><b>DeadlockProneLockStrategy:</b> Acquires locks in raw caller order with an artificial delay window,
 *       reliably triggering Coffman's Circular Wait deadlock under cross-transfers.</li>
 *   <li><b>TimedLockStrategy:</b> Uses {@link ReentrantLock#tryLock(long, TimeUnit)} with exponential backoff
 *       to prevent indefinite thread blocking under heavy contention.</li>
 * </ul>
 *
 * @author Danyal Aqeel
 */
public interface LockStrategy {

    /**
     * Executes the specified critical section while holding locks on both accounts.
     *
     * @param acc1   first account
     * @param acc2   second account
     * @param action critical section to execute inside lock boundaries
     */
    void executeWithLocks(Account acc1, Account acc2, Runnable action);

    /**
     * Returns the human-readable name of this strategy.
     *
     * @return strategy identifier name
     */
    String getStrategyName();

    // =========================================================================
    // 1. Safe Lock Strategy (Deterministic Ordering)
    // =========================================================================

    /**
     * Production-grade safe strategy delegating to {@link AccountLockManager} for deterministic ID sorting.
     */
    class SafeLockStrategy implements LockStrategy {
        @Override
        public void executeWithLocks(Account acc1, Account acc2, Runnable action) {
            AccountLockManager.executeWithLocks(acc1, acc2, action);
        }

        @Override
        public String getStrategyName() {
            return "Deterministic Safe Strategy (Zero Deadlocks)";
        }
    }

    // =========================================================================
    // 2. Deadlock-Prone Lock Strategy (Naive Caller Ordering with Artificial Delay)
    // =========================================================================

    /**
     * Naive strategy created specifically to demonstrate and detect Coffman Circular Wait deadlocks in test suites.
     */
    class DeadlockProneLockStrategy implements LockStrategy {
        private final long artificialDelayMs;

        public DeadlockProneLockStrategy() {
            this(50);
        }

        public DeadlockProneLockStrategy(long artificialDelayMs) {
            this.artificialDelayMs = artificialDelayMs;
        }

        @Override
        public void executeWithLocks(Account acc1, Account acc2, Runnable action) {
            if (acc1 == null || acc2 == null) {
                throw new IllegalArgumentException("Account references cannot be null");
            }

            ReentrantLock lock1 = acc1.getLock();
            ReentrantLock lock2 = acc2.getLock();

            lock1.lock();
            try {
                // Artificial window enabling concurrent thread to acquire lock2 first
                if (artificialDelayMs > 0) {
                    try {
                        Thread.sleep(artificialDelayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                lock2.lock();
                try {
                    action.run();
                } finally {
                    lock2.unlock();
                }
            } finally {
                lock1.unlock();
            }
        }

        @Override
        public String getStrategyName() {
            return "Naive Naive Strategy (Deadlock Prone)";
        }
    }

    // =========================================================================
    // 3. Timed Lock Strategy (tryLock with Backoff)
    // =========================================================================

    /**
     * Non-blocking strategy that uses {@code tryLock()} with backoff to recover from lock contention.
     */
    class TimedLockStrategy implements LockStrategy {
        private final long timeoutMs;
        private final int maxRetries;

        public TimedLockStrategy() {
            this(100, 3);
        }

        public TimedLockStrategy(long timeoutMs, int maxRetries) {
            this.timeoutMs = timeoutMs;
            this.maxRetries = maxRetries;
        }

        @Override
        public void executeWithLocks(Account acc1, Account acc2, Runnable action) {
            if (acc1 == null || acc2 == null) {
                throw new IllegalArgumentException("Account references cannot be null");
            }

            ReentrantLock lock1 = acc1.getLock();
            ReentrantLock lock2 = acc2.getLock();

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                boolean gotLock1 = false;
                boolean gotLock2 = false;
                try {
                    gotLock1 = lock1.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
                    if (gotLock1) {
                        gotLock2 = lock2.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
                        if (gotLock2) {
                            action.run();
                            return; // Successful execution
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted during lock acquisition", e);
                } finally {
                    // Release acquired locks if both were not secured
                    if (!(gotLock1 && gotLock2)) {
                        if (gotLock2) lock2.unlock();
                        if (gotLock1) lock1.unlock();
                    } else {
                        // Action executed successfully inside try block, unlock both now
                        lock2.unlock();
                        lock1.unlock();
                    }
                }

                // Random backoff before retrying
                try {
                    Thread.sleep(10 + (long) (Math.random() * 20));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted during backoff", e);
                }
            }

            throw new IllegalStateException(String.format(
                    "TimedLockStrategy failed to acquire locks for accounts [%s, %s] after %d attempts",
                    acc1.getId(), acc2.getId(), maxRetries));
        }

        @Override
        public String getStrategyName() {
            return "Timed Backoff Strategy (Non-Blocking Recovery)";
        }
    }
}
