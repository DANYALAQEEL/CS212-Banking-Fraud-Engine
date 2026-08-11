# Day 8 Architectural Breakdown & Viva Defense Guide

**Author:** Muhammad Arslan (`arslan2147c@gmail.com`)  
**Component:** Phase 4 — UI Controller Integration & Asynchronous Thread Handoff  
**Commit Range:** C12 & C14  
**Date:** August 02, 2026  

---

## 1. Executive Summary & Why We Built This

On Day 8, we connected the JavaFX frontend layout (`main_dashboard.fxml`) to our concurrent backend banking engine (`TransferEngine`, `FraudDetectionEngine`, `TransactionPipelineQueue`) using an Model-View-Controller (MVC) architecture.

### Key Architectural Challenge: Thread Handoff
In JavaFX, user interface components can **only** be modified on a single thread called the **JavaFX Application Thread**. If a background worker thread (executing financial transfers or deadlock simulation) attempts to update a `TableView` or `Label` directly, JavaFX throws an `IllegalStateException: Not on FX application thread`.

Conversely, if long-running transfers run directly on the UI thread, the entire GUI freezes ("Not Responding").

To solve this, we implemented `JavaFXEventListener` and `JavaFXEventBridge`, which route telemetry events from background worker threads safely onto the UI thread using `Platform.runLater()`.

---

## 2. Component-by-Component Analysis

### A. `DashboardController.java`
* **Role:** FXML Controller implementing `Initializable` and `JavaFXEventListener`.
* **Account Seeding:** Initializes 6 demo accounts across all three domain subclasses (`SavingsAccount`, `CheckingAccount`, `CreditAccount`).
* **Table Value Factory Bindings:** Binds `TableView<Account>` and `TableView<Transaction>` properties dynamically.
* **Control Actions:**
  1. `handleExecuteTransfer()`: Reads inputs, selects `LockStrategy` (Safe, Deadlock-Prone, Timed), and dispatches task to background `ExecutorService`.
  2. `handleInjectTraffic()`: Fires 7 rapid micro-transfers to trigger `FraudDetectionEngine` velocity rules in real time.
  3. `handleTriggerDeadlock()`: Spawns two opposing cross-locking threads using `DeadlockProneLockStrategy`, detecting deadlocks via `ThreadMXBean`.
  4. `handleResetSystem()`: Restores initial account balances and clears transaction logs.

### B. `JavaFXEventListener.java` & `JavaFXEventBridge`
* **Role:** Event listener interface and thread handoff bridge.
* **Thread Safety:** `JavaFXEventBridge.publishTransactionEvent()` wraps listener dispatches inside `Platform.runLater(() -> listener.onTransactionProcessed(...))`, guaranteeing zero GUI thread violations.

---

## 3. OOP Concepts & Design Patterns Applied

| OOP Concept / Pattern | Application in Day 8 Code |
| :--- | :--- |
| **Model-View-Controller (MVC)** | `main_dashboard.fxml` (View), `DashboardController.java` (Controller), `Account`/`Transaction` (Model). |
| **Observer Pattern / Event Bridge** | `JavaFXEventListener` notifies controller when background threads complete transfers. |
| **Strategy Pattern Integration** | Controller dynamically injects `SafeLockStrategy`, `DeadlockProneLockStrategy`, or `TimedLockStrategy` based on UI ComboBox selection. |
| **Encapsulation & Concurrency Guarding** | Background worker threads interact with `Account` objects strictly inside `AccountLockManager` boundaries. |

---

## 4. Day 8 Viva Defense Q&A (WhatsApp Study Notes)

### Q1: "Arslan, how did you connect background worker threads to the JavaFX GUI without causing thread safety crashes?"
> **Answer:** *"We built an Event Bridge (`JavaFXEventBridge`) using the Observer Pattern. When a background thread completes a transaction or detects a fraud alert, it calls `JavaFXEventBridge.publishTransactionEvent()`. The bridge wraps the event inside `Platform.runLater()`, which queues the UI table update onto the JavaFX Application Thread without blocking worker threads."*

### Q2: "How does the UI controller demonstrate both safe execution and deadlocks?"
> **Answer:** *"The UI includes a strategy dropdown selector. When 'Deterministic Safe Strategy' is selected, transfers sort account IDs to break circular wait. When 'Naive Strategy' is selected, two threads lock accounts in reverse order, triggering a deadlock that our background `ThreadMXBean` monitor catches and displays as an alert banner."*

### Q3: "What happens when you click 'Inject High-Velocity Traffic'?"
> **Answer:** *"The controller submits a task to an `ExecutorService` that fires 7 rapid micro-transfers between accounts. The `FraudDetectionEngine` evaluates each transfer against its sliding-window history, flags high-velocity transactions as `FLAGGED_SUSPICIOUS`, and updates the UI stream in real time."*
