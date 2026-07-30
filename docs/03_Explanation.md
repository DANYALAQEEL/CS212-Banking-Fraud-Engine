# DAY 3 COMPREHENSIVE ARCHITECTURAL EXPLANATION & VIVA GUIDE

**Project:** Automated Banking & Fraud Detection System  
**Phase:** Phase 2 — Concurrency Engine & Lock Architecture (Day 3 Milestone)  
**Assigned Commit Owner:** Danyal Aqeel (Commits C06 & C07)  

---

## 1. What We Built & Why We Built It

### 1.1 Lock Ordering Manager (`AccountLockManager.java`)
* **What We Did:** Implemented a thread-safe utility class that enforces deterministic lock ordering when acquiring locks on any pair of `Account` instances.
* **Why We Did It:** 
  * In a multi-threaded system where transfers occur in parallel between arbitrary account pairs ($A \rightarrow B$ and $B \rightarrow A$), acquiring locks out of order causes Coffman's **Circular Wait** deadlock—where Thread 1 holds lock A waiting for B, while Thread 2 holds lock B waiting for A.
  * `AccountLockManager` uses `compareTo()` (lexicographical sorting by Account ID) to sort accounts. Both threads lock the smaller ID first, then the larger ID. This makes circular wait mathematically impossible.

### 1.2 Core Execution Engine (`TransferEngine.java`)
* **What We Did:** Built the core execution engine that processes atomic fund transfers between accounts.
* **Why We Did It:** 
  * Fulfills Category 3 of the rubric (**Flawless threading, no race conditions, perfect synchronization on shared data**).
  * Implements a **Two-Level TOCTOU (Time-of-Check to Time-of-Use) Guard**:
    1. *Time-of-Check (TOC):* Optimistic unsynchronized read outside the lock for fast rejection of obviously invalid balances.
    2. *Time-of-Use (TOU):* Mandatory atomic re-validation *inside* the lock boundary immediately before debiting to close the race window where a concurrent thread might have drained funds.

---

## 2. Deep Breakdown of Concurrency & OOP Concepts Used

### Concept 1: Deadlock Prevention via Lock Ordering
* **Where Used:** `AccountLockManager.executeWithLocks(acc1, acc2, action)`
* **How We Used It:**
  ```java
  Account firstLockAccount = acc1.compareTo(acc2) < 0 ? acc1 : acc2;
  Account secondLockAccount = firstLockAccount == acc1 ? acc2 : acc1;
  ```
* **Why We Used It:** Satisfies the formal OS proof for deadlock prevention: establishing a strict global total ordering on locks breaks the circular wait condition.

### Concept 2: Mutex Mutual Exclusion (`ReentrantLock`)
* **Where Used:** `Account.getLock()` inside `AccountLockManager`
* **How We Used It:** Acquired `firstLock.lock()` then `secondLock.lock()` inside nested `try-finally` blocks.
* **Why We Used It:** Guarantees mutual exclusion so no other thread can observe or mutate balances mid-transfer. Using nested `try-finally` blocks ensures that if an exception occurs or the second lock fails, `firstLock.unlock()` is unconditionally executed, preventing hold-count lock leaks.

### Concept 3: Atomic TOCTOU Re-Validation
* **Where Used:** `TransferEngine.processTransfer()` inside the lock lambda block.
* **How We Used It:** Re-read `fromAccount.getBalance()` inside the lock boundary to verify that balance $\ge$ amount before calling `fromAccount.debit(amount)`.
* **Why We Used It:** Eliminates race conditions where two concurrent transfer threads pass pre-flight checks simultaneously. Re-validating inside the lock boundary guarantees zero unhandled overdrafts.

---

## 3. Method-by-Method Architectural Explanation

### `AccountLockManager.java`
1. `executeWithLocks(acc1, acc2, action)`
   * **What it does:** Accepts two accounts and a `Runnable` critical section. Rejects self-transfers, sorts the accounts by ID, acquires both locks in ascending order, executes `action.run()`, and unlocks in reverse order inside `finally` blocks.
   * **Why:** Provides a single, centralized entry point for thread synchronization that makes deadlocks structurally impossible.

### `TransferEngine.java`
1. `processTransfer(fromAccount, toAccount, amount)`
   * **What it does:** Performs fast-fail checks, initiates deterministic locking via `AccountLockManager`, executes the TOCTOU re-validation, debits the source, credits the destination, and returns an immutable `Transaction` record stamped with `COMPLETED` or `FAILED_INSUFFICIENT_FUNDS`.
   * **Why:** Encapsulates transaction logic cleanly while maintaining 100% thread safety and zero partial-mutation side effects.

---

## 4. Viva Defense Q&A Master Sheet (Day 3)

**Q1: "Danyal, walk me through how `AccountLockManager` prevents circular wait deadlocks."**  
* **Answer:** *"Deadlocks require Coffman's Circular Wait condition. If Thread 1 transfers A to B and Thread 2 transfers B to A, acquiring locks out of order causes Thread 1 to hold A waiting for B while Thread 2 holds B waiting for A. Our `AccountLockManager` sorts account IDs lexicographically before acquiring locks. Both threads lock the smaller ID first, then the larger ID. Because all threads acquire locks in identical order, circular wait is mathematically impossible."*

**Q2: "What is the Time-of-Check to Time-of-Use (TOCTOU) gap, and how did you solve it?"**  
* **Answer:** *"TOCTOU occurs if we validate account balance before taking locks, because another thread can withdraw funds in the millisecond between validation and lock acquisition. We solved this by performing a mandatory atomic re-validation inside the lock boundary immediately after both account locks are acquired."*

**Q3: "Does `ReentrantLock` self-deadlock if a thread locks the same account twice?"**  
* **Answer:** *"No. `ReentrantLock` is reentrant—locking it again on the same thread increments the internal hold count. However, we validate self-transfers ($A \rightarrow A$) upfront to prevent domain logic errors and to prevent hold-count memory leaks where nested lock calls lack matching unlocks in `finally` blocks."*

**Q4: "Why did you use nested `try-finally` blocks when acquiring locks?"**  
* **Answer:** *"Nested `try-finally` blocks ensure exception safety. If `secondLock.lock()` throws an exception or the critical section fails, the outer `finally` block guarantees that `firstLock.unlock()` is unconditionally executed, preventing leaked locks."*
