# DAY 6 COMPREHENSIVE ARCHITECTURAL EXPLANATION & VIVA GUIDE

**Project:** Automated Banking & Fraud Detection System  
**Phase:** Phase 2 / 3 Transition — Deadlock Strategy Engine (Day 6 Milestone)  
**Assigned Commit Owner:** Danyal Aqeel (Commit C10)  

---

## 1. What We Built & Why We Built It

### 1.1 Strategy Design Pattern for Locking (`LockStrategy.java`)
* **What We Did:** Defined the `LockStrategy` interface and three concrete strategy implementations:
  1. `SafeLockStrategy`: Production-grade strategy enforcing total lexicographical lock ordering via `AccountLockManager` (0 deadlocks).
  2. `DeadlockProneLockStrategy`: Naive strategy acquiring locks in raw caller order with an artificial delay window, reliably triggering Coffman's **Circular Wait** deadlock.
  3. `TimedLockStrategy`: Non-blocking strategy utilizing `tryLock(timeout, unit)` with exponential backoff to recover gracefully from high lock contention.
* **Why We Did It:** 
  * Fulfills Category 3 & 4 of the rubric (**Advanced Multithreading & Design Patterns**).
  * Demonstrates both theoretical deadlock prevention proofs AND empirical deadlock detection under test conditions.
  * Allows the banking engine (and UI simulation modes) to dynamically switch between safe execution, deadlock demonstration mode, and non-blocking recovery.

### 1.2 Lock Strategy Test Suite & JVM MXBean Detection (`LockStrategyTest.java`)
* **What We Did:** Created a JUnit 5 test suite.
* **Why We Did It:**
  * Verifies `SafeLockStrategy` completes 100 parallel cross-transfers cleanly.
  * Uses JVM diagnostic telemetry (`ManagementFactory.getThreadMXBean().findDeadlockedThreads()`) to programmatically verify that `DeadlockProneLockStrategy` enters a true OS thread deadlock state.

---

## 2. Deep Breakdown of Concurrency & OOP Concepts Used

### Concept 1: Strategy Design Pattern (GoF)
* **Where Used:** `LockStrategy` interface and its 3 nested static inner classes.
* **How We Used It:**
  ```java
  public interface LockStrategy {
      void executeWithLocks(Account acc1, Account acc2, Runnable action);
  }
  ```
* **Why We Used It:** Decouples concurrency synchronization algorithms from the `TransferEngine` caller. Allows swapping locking strategies at runtime without mutating business transfer code.

### Concept 2: Programmatic Deadlock Detection (`ThreadMXBean`)
* **Where Used:** `LockStrategyTest.testDeadlockProneLockStrategyTriggersDeadlock()`
* **How We Used It:**
  ```java
  ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
  long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
  ```
* **Why We Used It:** Queries the JVM thread scheduler for cyclic wait dependencies. Returns an array of deadlocked thread IDs, proving empirically that circular wait occurred.

### Concept 3: Timed Non-Blocking Lock Acquisition (`tryLock`)
* **Where Used:** `TimedLockStrategy`
* **How We Used It:** Attempt `lock1.tryLock()`, then `lock2.tryLock()`. If `lock2` fails, unlock `lock1` immediately before backing off.
* **Why We Used It:** Prevents **Hold and Wait** (Coffman Condition 2). If the second lock cannot be acquired, holding the first lock while waiting causes deadlocks. Releasing the first lock on failure breaks the hold-and-wait condition.

---

## 3. Method-by-Method Architectural Explanation

### `LockStrategy.java`
1. `SafeLockStrategy.executeWithLocks()`
   * **What it does:** Sorts accounts lexicographically by ID and locks the lower ID first.
   * **Why:** Eliminates Circular Wait.

2. `DeadlockProneLockStrategy.executeWithLocks()`
   * **What it does:** Locks `acc1`, sleeps for 50ms, then attempts to lock `acc2`.
   * **Why:** Creates a high-probability race window where Thread 1 holds A waiting for B, while Thread 2 holds B waiting for A.

3. `TimedLockStrategy.executeWithLocks()`
   * **What it does:** Attempts `tryLock()` on both accounts. If either fails, releases all held locks, sleeps for random backoff (10–30ms), and retries up to `maxRetries`.
   * **Why:** Provides livelock-safe, non-blocking lock acquisition for high-throughput microservices.

---

## 4. Viva Defense Q&A Master Sheet (Day 6)

**Q1: "Danyal, why did you implement the `LockStrategy` interface when `AccountLockManager` was already safe?"**  
* **Answer:** *"In software architecture, the Strategy Pattern allows us to isolate concurrency algorithms from business execution logic. By creating `LockStrategy`, our system can operate in production mode (`SafeLockStrategy`), demonstrate deadlocks for evaluation/testing (`DeadlockProneLockStrategy`), or use non-blocking timeouts (`TimedLockStrategy`) without modifying a single line of `TransferEngine` code."*

**Q2: "How did you programmatically prove that `DeadlockProneLockStrategy` causes a real Java thread deadlock?"**  
* **Answer:** *"We used Java's JVM Management API: `ManagementFactory.getThreadMXBean().findDeadlockedThreads()`. When Thread 1 (locking A then B) and Thread 2 (locking B then A) enter a circular wait state, the JVM thread scheduler detects the cyclic wait graph and `findDeadlockedThreads()` returns the thread IDs of the blocked threads."*

**Q3: "How does `TimedLockStrategy` avoid the 'Hold and Wait' deadlock condition?"**  
* **Answer:** *"If `TimedLockStrategy` successfully acquires `lock1` but fails to acquire `lock2` within the timeout, it executes a `finally` block that immediately releases `lock1` before sleeping for random backoff. Releasing held locks when progress cannot be made breaks the Hold and Wait condition."*

**Q4: "What is the difference between a deadlock and a livelock in `TimedLockStrategy`?"**  
* **Answer:** *"A deadlock occurs when threads block indefinitely on locks (`lock()`). A livelock occurs when threads repeatedly acquire and release locks in sync without making progress. We prevented livelocks in `TimedLockStrategy` by introducing randomized backoff intervals (10–30ms) between retry attempts."*
