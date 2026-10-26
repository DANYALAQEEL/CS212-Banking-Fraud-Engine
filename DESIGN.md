# DESIGN.md — Architecture of the CS-212 Banking & Fraud Detection Engine

This document describes the concurrency model, the fraud-detection pipeline, the lock-strategy
trade-offs, and the security controls of the engine. It explains *why* the design is correct.
For build/run instructions see [`README.md`](./README.md); for the audit history see
[`FINAL_REPORT.md`](./FINAL_REPORT.md).

---

## 1. System Concurrency Architecture & Lock Ordering Invariant

### 1.1 Shared state and the unit of locking

Each `Account` (`com.nust.banking.model.Account`) owns a single **fair** `ReentrantLock`
(`new ReentrantLock(true)`). The lock is `transient`, and `Account.readObject` creates it again on
deserialization, so a restored snapshot always comes back with fresh, unheld locks.

A transfer touches at least two accounts (source and destination), plus a third (`BANK-FEES`) when
`SettlementService` routes fees. Every multi-account mutation has to hold **all** of the relevant
locks at once, so the balance invariants (no overdraft past the limit, net position conserved)
are checked and applied atomically.

### 1.2 Coffman's four conditions

A deadlock can happen only when all four Coffman conditions hold at the same time:

| # | Condition | Status in this engine |
|---|-----------|-----------------------|
| 1 | **Mutual exclusion**: a resource is held by at most one thread | Holds. Locks are exclusive by design. |
| 2 | **Hold and wait**: a thread holds one lock while waiting for another | Holds. Nested acquisition is required. |
| 3 | **No preemption**: a lock cannot be forcibly taken away | Holds for `lock()`. |
| 4 | **Circular wait**: a cycle T₁ → T₂ → … → Tₙ → T₁ of "waits-for" edges | **Eliminated by lock ordering.** |

Breaking any one of the four conditions is enough. The engine breaks **circular wait**, which is the
only one that can be removed without giving up correctness (1), atomicity (2), or simplicity (3).

### 1.3 The ordering invariant

`AccountLockManager.executeWithLocks(Runnable, Account...)` enforces this rule:

> **Invariant:** every thread acquires account locks in strictly ascending order of
> `Account.getId()` under `String`'s natural (lexicographic) ordering. This is the same ordering as
> `Account.compareTo`.

The implementation:

1. Rejects `null` accounts and **duplicate IDs**. A duplicate would make the order non-strict and
   inflate the reentrant hold count.
2. Clones the argument array and sorts it with `Comparator.comparing(Account::getId)`.
3. Acquires the locks recursively in sorted order and releases them in reverse order inside
   `finally` blocks, so they are released even when the critical section throws.

### 1.4 Proof that circular wait is impossible

Let `≺` be the strict total order on account IDs. Account IDs are unique, so `≺` is total and
irreflexive.

* Suppose thread Tᵢ holds lock Lₐ and waits for lock L_b. Under the invariant, Tᵢ only requests
  L_b after it has acquired every lock it needs that precedes L_b, so **a ≺ b**. Each waits-for edge
  "Tᵢ holds Lₐ, waits for L_b" therefore points strictly *upward* in `≺`.
* A circular wait would need a cycle of such edges:
  `a₁ ≺ a₂ ≺ … ≺ aₙ ≺ a₁`.
* By transitivity this gives `a₁ ≺ a₁`, which contradicts irreflexivity. ∎

The proof covers any number of accounts per operation (the N-account overload) and any number of
threads, because it uses only the total order and nothing specific to a pair of accounts.

### 1.5 Why order by Account ID rather than `System.identityHashCode`

Ordering by `System.identityHashCode(obj)` is a common textbook technique, but it is weaker here:

| Criterion | `identityHashCode` | Natural Account ID (chosen) |
|-----------|--------------------|-----------------------------|
| Uniqueness | **Not guaranteed.** Two live objects can collide, so a global "tie-breaker" lock is needed. | Guaranteed. IDs are unique business keys, and duplicates are rejected up front. |
| Stability across JVM runs | Differs on every run | Stable, so traces and tests are reproducible |
| Survives (de)serialization | No. A restored object gets a new hash | Yes |
| Debuggability | Opaque integers | Readable `ACC-001 < ACC-002` |

Both techniques remove circular wait for the same reason (a strict total order). The ID comparator
gives that order without a tie-break path.

### 1.6 Thread inventory

| Thread / pool | Owner | Role |
|---------------|-------|------|
| JavaFX Application Thread | `MainApp` | UI only. It never takes account locks. Updates arrive via `Platform.runLater()`. |
| `pipeline-consumer-worker` × 3 (daemon) | `PipelineCoordinator` | Takes transactions off the queue and calls `SettlementService.settle(...)` with `SafeLockStrategy`. |
| Dashboard worker pool | `DashboardController` | Traffic injection and interest application. |
| `deadlock-demo-1/2` + 800 ms watchdog | `DashboardController` | A deliberate deadlock demo. The watchdog finds it with `ThreadMXBean.findDeadlockedThreads()` and recovers by interrupting threads blocked in `lockInterruptibly()`. |

---

## 2. Fraud Detection Pipeline

```
 producers ──put/offer──▶ TransactionPipelineQueue ──take──▶ consumer workers (×3)
 (UI, traffic            (bounded, priority-ordered,          │
  injector)               fair lock + 2 Conditions)           ▼
                                                     SettlementService.settle()
                                                       1. FraudDetectionEngine.evaluate (pre-transfer balance)
                                                       2. QUARANTINED → FLAGGED_FRAUD, balances untouched
                                                       3. otherwise → locked transfer + fee routing
                                                              │
                                                              ▼
                                                     JavaFXEventListener → Platform.runLater()
```

### 2.1 Priority queue semantics

`TransactionPipelineQueue` behaves like a `java.util.concurrent.PriorityBlockingQueue`, with one
important difference: **it is bounded**. `PriorityBlockingQueue` is unbounded, so `put()` never
blocks and a burst of traffic can grow the heap without limit. The engine therefore implements the
same priority-blocking semantics by hand:

* **Storage:** a `java.util.PriorityQueue<Transaction>` with initial capacity = bound.
* **Ordering:** amount **descending** (high-value transfers are screened first). Ties are broken by
  timestamp **ascending** (FIFO within equal amounts).
* **Synchronization:** one fair `ReentrantLock` and two `Condition`s.
  `notFull` blocks producers when `size == capacity` (**backpressure**), and `notEmpty` blocks
  consumers when the queue is empty.
* **Timed entry:** `offer(tx, timeout, unit)` lets producers give up instead of blocking forever.
* **Shutdown:** a `volatile boolean shutdown` flag combined with `signalAll()` wakes every waiter so
  that workers exit cleanly.
* **Telemetry:** `AtomicLong` counters for the number enqueued and dequeued, plus the current depth.

### 2.2 Consumer worker thread pool

`PipelineCoordinator` starts a fixed pool of **3 daemon consumer workers**. Each worker loops on
`take()`, resolves the source, destination, and fee accounts, and calls `SettlementService.settle()`
with `SafeLockStrategy`. The workers share no mutable state except the queue and the account locks,
so adding workers raises throughput without introducing new races.

### 2.3 Rule engine and sliding-window velocity checks

`FraudDetectionEngine` scores every transaction **before** settlement:

| Rule | Trigger | Score |
|------|---------|-------|
| Large Amount | `amount ≥ RS 100,000` | +40 |
| High Velocity | more than 5 transfers from the same source within 60 s | +35 |
| Suspicious Target | destination funded by more than 3 distinct sources within 60 s | +30 |
| Rapid Drain | `amount / preTransferBalance ≥ 0.90` | +25 |

The score is capped at 100. Verdicts: `< 40` **CLEARED**, `40–69` **FLAGGED_SUSPICIOUS**,
`≥ 70` **QUARANTINED** (blocked, and balances are not touched).

**Sliding windows.** The velocity rules use a
`ConcurrentHashMap<String, Deque<…>>` with one `ArrayDeque` per account key:

1. `computeIfAbsent` atomically creates the deque for a key.
2. The thread `synchronized (window)` locks **only that key's deque**. This per-key monitor means
   unrelated accounts never contend with each other.
3. Entries older than `VELOCITY_WINDOW` (60 s) are removed from the head. The deque is in time order,
   so eviction is amortised O(1).
4. The thread appends the new event and returns the count in the window (or the distinct-source
   count).

`pruneExpiredWindows(now)` removes keys whose windows are empty, so memory stays bounded by the
number of *active* accounts.

The fraud engine's monitors are leaf locks. They are never held while an account lock is being
acquired, so they cannot form a cycle with the account lock order.

---

## 3. Comparative Benchmark Analysis of Lock Strategies

All three strategies implement `LockStrategy.executeWithLocks(acc1, acc2, action)` and can be swapped
at runtime through `LockStrategy.StrategyChoice` (SAFE / NAIVE / TIMED).

| Strategy | Acquisition rule | Coffman condition broken | Deadlock outcome | Failure mode |
|----------|------------------|--------------------------|------------------|--------------|
| **`SafeLockStrategy`** | Sorted by Account ID (via `AccountLockManager`) | Circular wait | **0 deadlocks, guaranteed** (§1.4) | None. Blocking `lock()` waits but always makes progress. |
| **`DeadlockProneLockStrategy`** | Caller order, with an artificial delay (default 50 ms) between the first and second lock | None | **Circular wait reproduced deterministically** in A→B / B→A cross-transfers | Threads block forever. Detected by `ThreadMXBean`. |
| **`TimedLockStrategy`** | Caller order with `tryLock(timeout)`, and full release between attempts | No preemption (voluntary release) and hold-and-wait (release on failure) | No permanent deadlock | Bounded retries with exponential backoff and jitter (`(10 << attempt) + rand(0..20) ms`). Throws `IllegalStateException` once retries are exhausted. Livelock is possible but self-limiting. |

### 3.1 Reported figures

These figures come from the project's audit benchmark ([`FINAL_REPORT.md` §6](./FINAL_REPORT.md)),
1,000 transactions per strategy:

| Strategy | Avg latency | Throughput | Deadlock risk |
|----------|-------------|------------|---------------|
| SAFE (sorted ID) | 1.2 ms | ≈ 833 tps | 0 % |
| NAIVE (caller order) | n/a: hangs | n/a | Certain under cross-transfer |
| TIMED (backoff) | 4.8 ms | ≈ 208 tps | 0 % permanent (retries can be exhausted) |

### 3.2 Interpretation

* **SAFE is the production default.** It removes deadlock by construction at the cost of one
  O(n log n) sort over n ≤ 3 accounts, which is negligible.
* **TIMED is about 4× slower** because a failed attempt throws away the work already done, then
  sleeps for the backoff. Its value is *liveness under unknown lock orders*, for example when it has
  to integrate with code that does not follow the ID invariant.
* **NAIVE exists only for teaching and verification.** It proves that the deadlock detector and the
  SAFE invariant really matter.

### 3.3 Verification in the test suite (`LockStrategyTest`)

* `testSafeLockStrategyZeroDeadlocks` runs 100 tasks on 10 threads. The tasks **alternate
  direction** (`accA → accB` on even indices, `accB → accA` on odd), and all of them must finish
  within the timeout. Running opposing transfers at the same time is the scenario that would create
  a cycle without ordering.
* `testDeadlockProneLockStrategyProducesDeadlock` makes two opposing threads deadlock, then asserts
  that `findDeadlockedThreads()` reports at least 2 threads.
* `testTimedLockStrategyNoDeadlockUnderContention` runs 20 **bidirectional** tasks on 5 threads.
  Every task either succeeds or fails cleanly through retry exhaustion, and none hangs.
* `testTimedLockStrategyExhaustsRetries` holds a lock externally and asserts that an
  `IllegalStateException` follows.

---

## 4. Security Architecture: Snapshot Persistence

### 4.1 Threat

`BinaryStateSerializer.loadSnapshot(File)` reads a Java-serialized `EngineSnapshot`. Unfiltered
`ObjectInputStream.readObject()` will instantiate **any** `Serializable` class on the classpath
before the cast to `EngineSnapshot` ever runs. A tampered snapshot file is therefore a classic
deserialization-gadget vector, leading to remote code execution or denial of service.

### 4.2 Control: `ObjectInputFilter` allowlist (JEP 290)

Before the first `readObject()`, the stream gets a pattern-based filter:

```java
ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
        "com.nust.banking.persistence.BinaryStateSerializer$EngineSnapshot;" +
        "com.nust.banking.model.*;" +
        "java.util.*;" +
        "java.lang.*;" +
        "java.time.Instant;" +
        "java.time.Ser;" +
        "!*"
);
ois.setObjectInputFilter(filter);
```

| Pattern | Admits | Why it is needed |
|---------|--------|------------------|
| `…BinaryStateSerializer$EngineSnapshot` | The root record only | The top-level object |
| `com.nust.banking.model.*` | `Account`, `SavingsAccount`, `CheckingAccount`, `CreditAccount`, `Transaction`, `Transaction$TransactionStatus`, … | Domain payload |
| `java.util.*` | `ArrayList`, `ImmutableCollections`' `CollSer` proxy, `HashMap`, … | Collection fields (`List`, `Map`) |
| `java.lang.*` | `String`, `Enum`, `Long`, `Object[]` component type, … | Primitive wrappers, enums, collection backing arrays |
| `java.time.Instant`, `java.time.Ser` | `Instant` and its serialization proxy | `Transaction.timestamp` is an `Instant`. `Instant` writes a `Ser` proxy, and on JDK 17+ the filter also checks the class `readResolve` returns. |
| `!*` | **Rejects everything else** | Default-deny |

**Semantics to keep in mind:**

* A `pkg.*` pattern matches classes **directly** in that package, not in its sub-packages. For
  example, `java.util.*` does **not** admit `java.util.concurrent.*`. That is why the `transient`
  `ReentrantLock` is not serialized and is rebuilt in `Account.readObject`.
* Patterns are evaluated left to right, and the first match wins, so `!*` must stay last.
* For arrays, the filter checks the component type.
* Serialization proxies are checked twice: once for the proxy class (`java.time.Ser`) and once for
  the object its `readResolve` returns (`java.time.Instant`). Both must be allowed.
* A rejected class throws `java.io.InvalidClassException` ("filter status: REJECTED"). The class is
  never instantiated.

### 4.3 Maintenance rule

When a new `Serializable` field is added to `Account`, `Transaction`, or `EngineSnapshot`, add the
**narrowest** pattern that covers its runtime class to the filter. Prefer an exact class name to a
package wildcard. Then extend `PersistenceTest.testBinaryStateSerialization` to round-trip the new
field. Never widen the filter to `java.**` or remove the trailing `!*`.

### 4.4 Defence in depth (recommended next steps)

* Add resource limits such as `maxdepth=…;maxrefs=…;maxbytes=…` to the filter string to bound the
  cost of a malicious object graph.
* Sign or HMAC snapshot files so that tampering is detected before deserialization.
* For untrusted sources, prefer the CSV and JSON formats in `LedgerFileManager`, which never execute
  class-loading logic.

---

## 5. Continuous Integration

[`.github/workflows/ci.yml`](./.github/workflows/ci.yml) runs `mvn clean test --batch-mode` on
Temurin JDK 21 (Maven cache enabled) for every push and pull request to `main`, and on manual
`workflow_dispatch`. The 55-test JUnit 5 suite has to pass before a change is merged.
