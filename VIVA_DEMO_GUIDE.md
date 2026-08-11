# CS-212 OOP Final Project — Defense, Viva & Live Demo Master Guide

**Course:** CS-212 Object-Oriented Programming (Summer 2026, NUST SEECS)  
**Project Title:** Automated High-Throughput Banking & Real-Time Fraud Detection Engine  
**Team Members & Roster:**
* **Danyal Aqeel** (`raqeel.bese24seecs@seecs.edu.pk`) — Lead Architect (Concurrency Engine & Lock Architecture)
* **Muhammad Arslan** (`arslan2147c@gmail.com`) — Senior Developer (JavaFX UI & Live Telemetry Dashboard)
* **Hamza Zahoor** (`hamzazahoor769@gmail.com`) — Junior Developer (Domain Models & File Persistence)

---

## 💡 Professor's Viva Mindset & Defense Strategy

Based on your professor's defense style, he follows a strict **"Code First, Visuals Second"** protocol:
1. **Code Inspection First:** He opens the source files and UML diagram *before* running the app.
2. **Code Ownership Verification:** He asks line-by-line questions to verify that the code was written by hand and understood deeply.
3. **Concurrency & Thread Deep Dive:** He focuses heavily on synchronization, thread creation, and deadlock handling.
4. **OOP & SOLID Principles Mapping:** He opens `UML_v2.pdf` and asks you to point out where specific OOP and SOLID principles are implemented in code.
5. **Live Functionality Proof:** He asks you to run the app and demonstrate every feature working cleanly.

---

## 🏛️ PART 1: OOP Concepts & SOLID Principles Mapping

### 1. The Four Pillar OOP Concepts in Code

#### **A. Abstraction**
* **Where:** Abstract class [`Account.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/model/Account.java#L14) and interface [`LockStrategy.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/engine/LockStrategy.java#L22).
* **Explanation:** `Account` defines common banking properties (`id`, `ownerName`, `balance`, `lock`) while declaring the abstract method `public abstract void debit(double amount)` (line 96). It hides specific withdrawal and fee logic from high-level callers like `TransferEngine`.

#### **B. Inheritance**
* **Where:** Subclasses [`SavingsAccount.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/model/SavingsAccount.java), [`CheckingAccount.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/model/CheckingAccount.java), and [`CreditAccount.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/model/CreditAccount.java).
* **Explanation:** All three subclasses extend `Account` (`public class SavingsAccount extends Account`), inheriting balance tracking and lock synchronization while extending functionality (e.g., `SavingsAccount.applyMonthlyInterest()`).

#### **C. Encapsulation**
* **Where:** Private fields, immutable Records, and setter/getter guards across all models.
* **Explanation:** 
  - `Account.java` (lines 17–20): Fields `id`, `ownerName`, `balance`, `lock` are `private`. Balance mutations occur strictly inside synchronized methods (`credit()`, `debit()`).
  - Immutable Java Records [`Transaction.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/model/Transaction.java) and [`FraudResult.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/model/FraudResult.java) enforce state immutability across background thread boundaries.

#### **D. Polymorphism**
* **Where:** Dynamic method dispatch in `TransferEngine.processTransfer()` and `Account.getNetPosition()`.
* **Explanation:** 
  - `Account.debit()` behaves polymorphically: `SavingsAccount` enforces minimum balance rules, `CheckingAccount` applies overdraft limits and transaction fees, and `CreditAccount` reduces available credit.
  - `CreditAccount` overrides `credit()` (lines 25–30) to pay down debt balance and `getNetPosition()` (lines 33–35) to return a negative liability contribution (debt).

---

### 2. SOLID Principles in Code

| SOLID Principle | Implementation File & Location | Code Mechanism & Explanation |
|---|---|---|
| **S — Single Responsibility (SRP)** | [`SettlementService.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/engine/SettlementService.java#L13-L55) | `SettlementService` exclusively orchestrates pre-execution fraud evaluation and money movement. `FraudDetectionEngine` only scores risk; `TransferEngine` only mutates state. |
| **O — Open/Closed Principle (OCP)** | [`LockStrategy.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/engine/LockStrategy.java#L22-L218) | New locking algorithms can be added by implementing `LockStrategy` without altering `TransferEngine` or `SettlementService`. |
| **L — Liskov Substitution (LSP)** | [`AccountLockManager.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/engine/AccountLockManager.java#L36-L77) | Any subclass instance (`SavingsAccount`, `CheckingAccount`, `CreditAccount`) can be passed to `executeWithLocks(action, Account...)` without breaking sorting or lock acquisition. |
| **I — Interface Segregation (ISP)** | [`JavaFXEventListener.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/ui/JavaFXEventListener.java) | Clean, focused listener contracts (`onTransactionProcessed`, `onAccountBalanceChanged`, `onDeadlockDetected`) keeping UI listeners decoupled from engine internals. |
| **D — Dependency Inversion (DIP)** | [`PipelineCoordinator.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/engine/PipelineCoordinator.java#L21-L39) | High-level `PipelineCoordinator` depends on `SettlementService` and `JavaFXEventListener` abstractions rather than concrete GUI components. |

---

## ⚡ PART 2: Concurrency, Synchronization & Threading Deep Dive

### 1. Where & How Synchronization is Implemented

#### **A. ReentrantLock & Deterministic N-Account Ordering**
* **File:** [`AccountLockManager.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/engine/AccountLockManager.java#L36-L77)
* **How it works:**
  1. Accepts an arbitrary array of accounts (`Account... accounts`).
  2. Rejects duplicates using a `HashSet` (lines 44–55) to prevent circular wait self-deadlocks.
  3. Sorts the accounts lexicographically by `Account.getId()` (`Arrays.sort(sortedAccounts, Comparator.comparing(Account::getId))`, line 59).
  4. Acquires locks recursively in strict ascending ID order (`lock.lock()`, line 71) and releases them in reverse order inside a `try-finally` block (lines 72–76).
* **Why:** Breaks Coffman's **Circular Wait** condition for arbitrary $N$-account transfers.

#### **B. Volatile Keyword & Fair Lock Policy**
* **File:** [`Account.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/model/Account.java#L19-L35)
* **How it works:**
  - `private volatile double balance;` (line 19) guarantees immediate memory visibility across CPU caches without full lock overhead for point-in-time reads (`getBalance()`, line 55).
  - `this.lock = new ReentrantLock(true);` (line 35) configures a **fair lock policy**, serving waiting threads in FIFO order to prevent thread starvation.

#### **C. Per-Key Monitor Synchronization on ArrayDeque**
* **File:** [`FraudDetectionEngine.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/engine/FraudDetectionEngine.java#L125-L151)
* **How it works:**
  - Uses `ConcurrentHashMap` for history maps (`sourceVelocityMap`, `targetPatternMap`).
  - Synchronizes on per-account `Deque` instances (`synchronized (window)`) during window pruning and entry insertion (lines 127, 139).
* **Why:** Prevents `ConcurrentModificationException` and data races while keeping lock granularity isolated per account.

#### **D. Dual Condition Variables (`notFull`, `notEmpty`) on Bounded Queue**
* **File:** [`TransactionPipelineQueue.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/engine/TransactionPipelineQueue.java#L30-L195)
* **How it works:**
  - `ReentrantLock lock` with two conditions: `notFull = lock.newCondition();` and `notEmpty = lock.newCondition();`.
  - `put()`/`offer()` awaits `notFull` when `queue.size() == capacity` (line 80, 113).
  - `take()`/`poll()` awaits `notEmpty` when `queue.isEmpty()` (line 138, 170).
* **Why:** Enforces active bounded backpressure (max 100 items) for streaming transactions.

#### **E. JavaFX UI Thread Handoff (`Platform.runLater()`)**
* **File:** [`JavaFXEventListener.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/ui/JavaFXEventListener.java#L35-L65) & [`DashboardController.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/ui/DashboardController.java#L550-L580)
* **How it works:** `JavaFXEventBridge` checks `Platform.isFxApplicationThread()`. If false, wraps UI update code inside `Platform.runLater(task)`.
* **Why:** JavaFX controls can only be modified on the FX Application Thread. Direct updates from worker threads crash the runtime.

---

### 2. Thread Inventory: Where, How & Why Threads Are Created

| Thread Category | File & Location | Creation Mechanism | Quantity & Type | Purpose & Why |
|---|---|---|---|---|
| **Pipeline Consumer Workers** | [`PipelineCoordinator.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/engine/PipelineCoordinator.java#L32-L36) | `Executors.newFixedThreadPool(3, threadFactory)` | **3 Daemon Threads** (`pipeline-consumer-worker`) | Asynchronously consumes transactions from `TransactionPipelineQueue`, runs fraud evaluation, and executes settlement. |
| **GUI Worker Pool** | [`DashboardController.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/ui/DashboardController.java#L94-L100) | `new ThreadPoolExecutor(5, 5, ...)` | **5 Daemon Threads** (`settlement-worker`) | Executes non-blocking background tasks (CSV/JSON/Binary File I/O, monthly interest calculations). |
| **JMX Deadlock Watchdog** | [`DashboardController.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/ui/DashboardController.java#L359-L370) | `Executors.newSingleThreadScheduledExecutor()` | **1 Scheduled Thread** (800ms one-shot) | Runs `ThreadMXBean.findDeadlockedThreads()`. If circular deadlock occurs on demo accounts, interrupts deadlocked threads. |
| **JavaFX Telemetry Timeline** | [`DashboardController.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/ui/DashboardController.java#L136-L138) | `new Timeline(new KeyFrame(Duration.seconds(1), ...))` | **1 FX Application Thread Timer** | Refreshes top metric cards (Liquidity, Queue Depth, Active Threads, TPS, Risk Score) every 1 second. |
| **Traffic Injector Threads** | [`DashboardController.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/ui/DashboardController.java#L293-L305) | `workerThreadPool.submit(...)` | **7 Concurrent Tasks** gated by `CountDownLatch` | Simultaneously enqueues transfers to stress-test queue depth and lock safety. |

---

## 📁 PART 3: File Handling & State Persistence Deep Dive

### 1. CSV Ledger Persistence (`LedgerFileManager.java`)
* **Exporting (`exportAccountsToCsv`, lines 22–56):**
  - Iterates through `Account` collection.
  - Escapes comma-containing owner names (e.g. `"Smith, Alice"`, line 34).
  - Writes polymorphically formatted CSV lines specifying account type (`SavingsAccount`, `CheckingAccount`, `CreditAccount`) and parameters.
* **Importing (`importAccountsFromCsv`, lines 61–103):**
  - Parses CSV lines using regex split respecting quotes: `line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")` (line 74).
  - Preserves negative balances for overdrawn checking accounts (`chk.setBalanceDirectly(balance)`, lines 90–92).

### 2. JSON Audit Log Export (`LedgerFileManager.java`)
* **Exporting (`exportTransactionsToJson`, lines 108–137):**
  - Formats transaction stream into structured JSON array.
  - Character Escaping (`escapeJson`, lines 139–148): Replaces control characters (`\n`, `\t`, `\"`, `\\`) to prevent output corruption.

### 3. Java Binary State Snapshots (`BinaryStateSerializer.java`)
* **Snapshot Model:** [`EngineSnapshot`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/persistence/BinaryStateSerializer.java#L20-L27) record encapsulating timestamp, accounts, transactions, and metadata.
* **Lock Re-initialization on Deserialization (`Account.java`):**
  - `lock` field is marked `transient` because `ReentrantLock` is not serializable.
  - Overrides `readObject(ObjectInputStream in)` (lines 38–41) to re-instantiate `this.lock = new ReentrantLock(true);` upon state restoration.

### 4. Team Division of Labor
* **Danyal Aqeel (Lead):** Concurrency Engine (`AccountLockManager`, `TransferEngine`, `LockStrategy`), `FraudDetectionEngine`, `TransactionPipelineQueue`.
* **Muhammad Arslan (Senior):** JavaFX UI (`MainApp`, `DashboardController`), `TelemetryViewController`, `JavaFXEventBridge`, CSS tokens, FXML layout.
* **Hamza Zahoor (Junior):** Domain Models (`Account`, `SavingsAccount`, `CheckingAccount`, `CreditAccount`), `LedgerFileManager` (CSV/JSON), `BinaryStateSerializer`.

---

## 🎬 PART 4: Live Demo Execution Script (Step-by-Step)

When the professor says **"Now run the code and show me all the functionalities"**, follow this exact sequence:

### **Step 1: Launch the Application**
Double-click [`RUN_BANKING_APP.bat`](file:///C:/Users/Administrator/Desktop/RUN_BANKING_APP.bat) on Desktop or run `RUN_APP.bat` in project root.  
*Point out:* Initial balances load automatically into the Account Ledger table (`ACC-001` through `ACC-006` and `BANK-FEES`).

### **Step 2: Execute a Normal Transfer (CLEARED / COMPLETED)**
1. Select **Source Account:** `ACC-006` (Frank Wright, CreditAccount).
2. Select **Destination Account:** `ACC-005` (Emma Watson, CheckingAccount).
3. Amount: `5000.00`. Strategy: `Deterministic Safe Strategy`.
4. Click **Execute Transfer**.
5. *Show Professor:* Status displays `COMPLETED`. Live transaction stream records transaction. `ACC-006` credit balance decreases, `ACC-005` balance increases. Total liquidity remains invariant (`RS 1,180,000.00`).

### **Step 3: Trigger Pre-Execution Fraud Quarantining (FLAGGED_FRAUD)**
1. Select **Source Account:** `ACC-001` (Alice Smith, balance RS 500,000.00).
2. Enter **Amount:** `480,000.00` (exceeds 90% balance $\rightarrow$ RAPID_DRAIN + LARGE_AMOUNT = Risk Score 65).
3. Click **Execute Transfer** once, then immediately click **Execute Transfer** again.
4. *Show Professor:* Second transfer triggers HIGH_VELOCITY (+35) $\rightarrow$ **Risk Score hits 100 (QUARANTINED)**. Transaction status becomes `FLAGGED_FRAUD` (blocked by pre-execution `SettlementService`). **Account balances are untouched (0 money moved)**.

### **Step 4: Inject Concurrent Traffic (7 Worker Threads)**
1. Click **Inject Traffic**.
2. *Show Professor:* `lblActiveThreads` metric jumps to `5/5 ACTIVE`, `lblQueueDepth` updates in real-time, TPS telemetry calculates live throughput, and all 7 concurrent transfers complete safely without race conditions.

### **Step 5: Demonstrate Deadlock Simulation & Watchdog Recovery**
1. Select **Lock Strategy:** `Naive Strategy (Deadlock Prone)`.
2. Click **Simulate Deadlock**.
3. *Show Professor:* Opposing threads acquire locks on `DEMO-A` and `DEMO-B` in raw caller order with artificial delay. Within 800ms, the JMX Watchdog thread detects Coffman's Circular Wait and interrupts the deadlocked threads cleanly: status updates to `DEADLOCK DETECTED & RECOVERED`.

### **Step 6: Apply Monthly Interest**
1. Click **Apply Monthly Interest**.
2. *Show Professor:* `SavingsAccount` balances (`ACC-001`, `ACC-004`) update automatically with monthly interest applied under lock protection.

### **Step 7: Demonstrate File Persistence**
1. Click **Save Ledger (CSV)** $\rightarrow$ exports ledger.
2. Click **Export Audit (JSON)** $\rightarrow$ exports transaction stream JSON with escaped control characters.
3. Click **Save Snapshot** $\rightarrow$ exports compressed binary engine state `.bin`.

---

## ❓ PART 5: Professor's Top 12 Viva Questions & Answers

#### **Q1: "Where is synchronization implemented in your project?"**
> **Answer:** "Synchronization is implemented at three distinct architectural layers:
> 1. **Low-level model state:** In [`Account.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/model/Account.java#L19) line 19 using `volatile double balance` for memory visibility and a fair `ReentrantLock` for exclusive access.
> 2. **Multi-account transfer boundary:** In [`AccountLockManager.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/engine/AccountLockManager.java#L36) line 36 using `executeWithLocks(action, Account...)` to sort account IDs before acquiring locks.
> 3. **Sliding-window tracking:** In [`FraudDetectionEngine.java`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/src/main/java/com/nust/banking/engine/FraudDetectionEngine.java#L127) line 127 using `synchronized(window)` monitors on `ArrayDeque` queues."

#### **Q2: "How do you guarantee that your transfers will never deadlock?"**
> **Answer:** "We break Coffman's **Circular Wait** condition by enforcing a strict global total ordering on account locks. In `AccountLockManager.java` line 59, we sort accounts by their unique Account ID string (`Arrays.sort(sortedAccounts, Comparator.comparing(Account::getId))`). Because every thread acquires locks in ascending ID order, a directed cycle in the Resource Allocation Graph is mathematically impossible."

#### **Q3: "Where are threads created in your system and why?"**
> **Answer:** "We have 4 distinct thread pools:
> 1. `PipelineCoordinator.java` line 32: 3 consumer worker threads (`pipeline-consumer-worker`) that process queue items asynchronously.
> 2. `DashboardController.java` line 94: 5 background worker threads (`settlement-worker`) that execute file I/O and heavy computations off the JavaFX Application Thread.
> 3. `DashboardController.java` line 359: 1 scheduled JMX watchdog thread running every 800ms to detect and interrupt deadlocked demo threads.
> 4. JavaFX Application Thread: Renders the GUI and handles UI events."

#### **Q4: "Why did you implement `SettlementService` instead of letting the UI call `TransferEngine` directly?"**
> **Answer:** "To solve a major defect where fraud detection ran *after* money had already moved. `SettlementService.java` line 41 evaluates fraud against the **pre-transfer balance** *before* invoking `TransferEngine.processTransfer()`. If risk score is $\ge 70$ (QUARANTINED), it marks the transaction `FLAGGED_FRAUD` and returns immediately without mutating account balances."

#### **Q5: "How is file handling implemented for CSV export and import?"**
> **Answer:** "In `LedgerFileManager.java`:
> - `exportAccountsToCsv()` (line 22) formats account objects into CSV lines, wrapping owner names containing commas in quotes (line 34).
> - `importAccountsFromCsv()` (line 61) uses regex split `line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")` (line 74) to parse quoted fields accurately and restores overdrawn checking accounts via `chk.setBalanceDirectly(balance)` (line 91)."

#### **Q6: "How does binary snapshotting work if `ReentrantLock` is not serializable?"**
> **Answer:** "In `Account.java` line 20, `lock` is marked `transient`. When `BinaryStateSerializer.saveSnapshot()` serializes an `EngineSnapshot`, Java skips the lock field. Upon deserialization, `Account.readObject(ObjectInputStream in)` (lines 38–41) executes `this.lock = new ReentrantLock(true);` to dynamically re-instantiate a fresh fair lock for the restored account."

#### **Q7: "Explain how Polymorphism is used in your domain model."**
> **Answer:** "In `Account.java`, `debit()` is an abstract method. Each subclass implements unique business logic:
> - `SavingsAccount.debit()` checks minimum balance constraints.
> - `CheckingAccount.debit()` allows overdraft up to a limit and deducts a transaction fee.
> - `CreditAccount.debit()` increases debt balance up to credit limit, overrides `credit()` to reduce debt, and overrides `getNetPosition()` to return a negative net liquidity position."

#### **Q8: "What is your Institutional Net Position Invariant and how do you prove it?"**
> **Answer:** "Total Institutional Net Position is $\sum \text{Assets} - \sum \text{Liabilities}$. In `Account.java` line 80, `getNetPosition()` returns `balance` for asset accounts and negative balance for `CreditAccount`. During a transfer of amount $X$ with fee $F$: $\Delta S = -(X+F)$, $\Delta D = +X$, $\Delta \text{Fees} = +F$. The sum of changes is $-(X+F) + X + F = 0$. Institutional net position is strictly conserved under all operations."

#### **Q9: "How does your queue handle backpressure when traffic spikes?"**
> **Answer:** "`TransactionPipelineQueue.java` line 55 is a bounded priority queue with capacity 100. It uses fair `ReentrantLock` with dual `Condition` variables (`notFull`, `notEmpty`). When the queue fills up, `offer(tx, timeout, unit)` (line 102) blocks producer threads for up to the timeout. If space does not free up, it returns `false`, signaling backpressure to the caller."

#### **Q10: "How do you ensure UI updates do not crash JavaFX from background threads?"**
> **Answer:** "In `JavaFXEventListener.java` line 35, `JavaFXEventBridge` checks `Platform.isFxApplicationThread()`. If the current thread is a background worker thread, it wraps the event notification inside `Platform.runLater(task)`, scheduling execution safely on the JavaFX Application Thread."

#### **Q11: "How does your Deadlock Watchdog work?"**
> **Answer:** "In `DashboardController.java` line 359, when `Simulate Deadlock` is clicked, two opposing threads attempt cross-locks using `LockStrategy.DeadlockProneLockStrategy`. An 800ms scheduled executor task calls `ThreadMXBean.findDeadlockedThreads()`. When deadlocks are detected, it calls `thread.interrupt()`, breaking `lockInterruptibly()` calls and restoring normal execution."

#### **Q12: "Which team member built which part of the codebase?"**
> **Answer:** 
> - **Danyal Aqeel (Lead):** Core concurrency engine (`AccountLockManager`, `TransferEngine`, `LockStrategy`), `FraudDetectionEngine`, `TransactionPipelineQueue`.
> - **Muhammad Arslan (Senior):** JavaFX UI architecture (`MainApp`, `DashboardController`), `TelemetryViewController`, `JavaFXEventBridge`, CSS tokens, FXML layout.
> - **Hamza Zahoor (Junior):** Domain model classes (`Account`, `SavingsAccount`, `CheckingAccount`, `CreditAccount`), `LedgerFileManager` (CSV/JSON), `BinaryStateSerializer`.
