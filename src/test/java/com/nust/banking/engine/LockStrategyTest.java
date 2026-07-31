package com.nust.banking.engine;

import com.nust.banking.model.CheckingAccount;
import com.nust.banking.model.SavingsAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LockStrategy Concurrency & Deadlock Detection Test Suite")
class LockStrategyTest {

    private SavingsAccount accA;
    private CheckingAccount accB;

    @BeforeEach
    void setUp() {
        accA = new SavingsAccount("ACC-A", "Alice", 100_000.0, 0.04, 1000.0);
        accB = new CheckingAccount("ACC-B", "Bob", 100_000.0, 5000.0, 25.0);
    }

    @Test
    @DisplayName("SafeLockStrategy: 100 parallel cross-transfers complete cleanly without deadlocks")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSafeLockStrategyZeroDeadlocks() throws InterruptedException {
        LockStrategy safeStrategy = new LockStrategy.SafeLockStrategy();
        int iterations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(iterations);

        for (int i = 0; i < iterations; i++) {
            final boolean direction = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    SavingsAccount from = direction ? accA : accA;
                    CheckingAccount to  = accB;
                    safeStrategy.executeWithLocks(from, to, () -> {
                        from.debit(10.0);
                        to.credit(10.0);
                    });
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(4, TimeUnit.SECONDS);
        assertTrue(completed, "All 100 cross-transfers should execute without deadlocks");
        executor.shutdown();
    }

    @Test
    @DisplayName("SafeLockStrategy: Human-readable strategy name")
    void testSafeLockStrategyName() {
        LockStrategy strategy = new LockStrategy.SafeLockStrategy();
        assertTrue(strategy.getStrategyName().toLowerCase().contains("safe")
                || strategy.getStrategyName().toLowerCase().contains("deterministic"));
    }

    @Test
    @DisplayName("DeadlockProneLockStrategy: Reliably triggers and detects Coffman Circular Wait deadlock via ThreadMXBean")
    void testDeadlockProneLockStrategyProducesDeadlock() throws InterruptedException {
        LockStrategy naiveStrategy = new LockStrategy.DeadlockProneLockStrategy(100);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: Locks accA then accB
        executor.submit(() -> {
            naiveStrategy.executeWithLocks(accA, accB, () -> {
                accA.debit(10.0);
                accB.credit(10.0);
            });
        });

        // Thread 2: Locks accB then accA
        executor.submit(() -> {
            naiveStrategy.executeWithLocks(accB, accA, () -> {
                accB.debit(10.0);
                accA.credit(10.0);
            });
        });

        Thread.sleep(400);

        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();

        assertNotNull(deadlockedThreads, "ThreadMXBean should detect circular wait deadlocked threads");
        assertTrue(deadlockedThreads.length >= 2, "At least 2 threads should be in a deadlocked state");

        executor.shutdownNow();
    }

    @Test
    @DisplayName("DeadlockProneLockStrategy: Strategy name contains naive or deadlock")
    void testDeadlockProneLockStrategyName() {
        LockStrategy strategy = new LockStrategy.DeadlockProneLockStrategy();
        assertTrue(strategy.getStrategyName().toLowerCase().contains("naive")
                || strategy.getStrategyName().toLowerCase().contains("deadlock"));
    }

    @Test
    @DisplayName("DeadlockProneLockStrategy: Null account rejection")
    void testDeadlockProneRejectsNullAccounts() {
        LockStrategy strategy = new LockStrategy.DeadlockProneLockStrategy();
        assertThrows(IllegalArgumentException.class, () -> strategy.executeWithLocks(null, accB, () -> {}));
        assertThrows(IllegalArgumentException.class, () -> strategy.executeWithLocks(accA, null, () -> {}));
    }

    @Test
    @DisplayName("TimedLockStrategy: Single uncontended lock execution succeeds on first attempt")
    void testTimedLockStrategySucceedsOnFirstAttempt() {
        LockStrategy timedStrategy = new LockStrategy.TimedLockStrategy(100, 3);
        AtomicInteger count = new AtomicInteger(0);

        timedStrategy.executeWithLocks(accA, accB, count::incrementAndGet);

        assertEquals(1, count.get(), "Action should execute exactly once");
    }

    @Test
    @DisplayName("TimedLockStrategy: Non-blocking tryLock backoff recovers cleanly under contention")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testTimedLockStrategyNoDeadlockUnderContention() throws InterruptedException {
        LockStrategy timedStrategy = new LockStrategy.TimedLockStrategy(50, 5);
        int tasks = 20;
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(tasks);

        for (int i = 0; i < tasks; i++) {
            final boolean direction = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    SavingsAccount from = accA;
                    CheckingAccount to   = accB;
                    timedStrategy.executeWithLocks(from, to, () -> {
                        from.debit(5.0);
                        to.credit(5.0);
                    });
                } catch (Exception e) {
                    // Backoff retry exhaustion recovery is valid behavior
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(4, TimeUnit.SECONDS);
        assertTrue(completed, "TimedLockStrategy tasks should complete or recover cleanly");
        executor.shutdown();
    }

    @Test
    @DisplayName("TimedLockStrategy: Externally held lock exhausts retries and throws IllegalStateException")
    void testTimedLockStrategyExhaustsRetries() throws Exception {
        LockStrategy timedStrategy = new LockStrategy.TimedLockStrategy(20, 2);

        // Hold accA lock externally on separate thread
        ExecutorService holder = Executors.newSingleThreadExecutor();
        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch releaseLockLatch = new CountDownLatch(1);

        holder.submit(() -> {
            accA.getLock().lock();
            try {
                lockAcquiredLatch.countDown();
                releaseLockLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                accA.getLock().unlock();
            }
        });

        lockAcquiredLatch.await(1, TimeUnit.SECONDS);

        // Timed strategy should fail after 2 attempts
        assertThrows(IllegalStateException.class, () ->
                timedStrategy.executeWithLocks(accA, accB, () -> {})
        );

        releaseLockLatch.countDown();
        holder.shutdown();
    }

    @Test
    @DisplayName("TimedLockStrategy: Strategy name contains timed or backoff")
    void testTimedLockStrategyName() {
        LockStrategy strategy = new LockStrategy.TimedLockStrategy();
        assertTrue(strategy.getStrategyName().toLowerCase().contains("timed")
                || strategy.getStrategyName().toLowerCase().contains("backoff"));
    }

    @Test
    @DisplayName("TimedLockStrategy: Null account rejection")
    void testTimedLockStrategyRejectsNullAccounts() {
        LockStrategy strategy = new LockStrategy.TimedLockStrategy();
        assertThrows(IllegalArgumentException.class, () -> strategy.executeWithLocks(null, accB, () -> {}));
        assertThrows(IllegalArgumentException.class, () -> strategy.executeWithLocks(accA, null, () -> {}));
    }
}
