# Day 11 Architectural Breakdown & Ultimate Viva Defense Guide

**Authors:** Danyal Aqeel (Lead), Hamza Zahoor (Junior), Muhammad Arslan (Senior)  
**Component:** Phase 5 — Final Submission Deliverables, Evolved UML v2.0 & Final Project Report  
**Commit Range:** C17 & C18  
**Date:** August 03, 2026  

---

## 1. Final Submission Overview

On Day 11, we finalized all academic deliverables for submission:
1. **Evolved UML Class Diagram v2.0 (`docs/UML_v2.md`):** Complete system-wide class diagram mapping all 5 phases across domain models, concurrency engines, JavaFX UI controllers, and persistence storage.
2. **Comprehensive Project Report (`docs/Project_Report.md`):** Academic report covering OOP principles, concurrency guarantees, sliding-window fraud algorithms, persistence, and test verification.
3. **Updated Status Dashboard (`STATUS.md`):** 100% completion across all 18 planned commits (C01 through C18).

---

## 2. Ultimate Team Viva Defense Cheat Sheet (WhatsApp Copy-Paste)

*(Share these key questions and answers across all team members for oral viva defense)*

***

### 🔴 CORE CONCURRENCY & DEADLOCK PREVENTION (Danyal Aqeel)

**Q1: "Danyal, how does your system guarantee zero deadlocks during high-speed concurrent transfers?"**  
> **Answer:** *"Deadlocks happen when two threads lock accounts in reverse order (Coffman's Circular Wait). We solved this in `AccountLockManager` by sorting account locks deterministically by Account ID (`compareTo`) before lock acquisition. Thread 1 and Thread 2 both lock the smaller ID first, breaking the circular wait condition completely."*

**Q2: "What is Two-Level TOCTOU, and why did you implement it in `TransferEngine`?"**  
> **Answer:** *"TOCTOU stands for Time-of-Check to Time-of-Use. If a thread checks an account balance before acquiring locks, another thread could withdraw money in between. We perform a second balance check inside the lock boundary right before debiting, ensuring atomic transfer safety."*

**Q3: "How does `TransactionPipelineQueue` prioritize high-value transactions under heavy traffic?"**  
> **Answer:** *"It uses a priority queue backed by `ReentrantLock` with dual `Condition` variables (`notFull`, `notEmpty`). High-amount transactions are assigned higher priority values so they are dequeued and settled first."*

***

### 🟢 DOMAIN MODELING & PERSISTENCE (Hamza Zahoor)

**Q4: "Hamza, explain how polymorphism works in your Account hierarchy."**  
> **Answer:** *"Account is an abstract base class with an abstract `debit(double amount)` method. `SavingsAccount` enforces minimum balances, `CheckingAccount` allows overdraft protection up to RS 5,000 with transaction fees, and `CreditAccount` tracks credit card debt against APR limit limits. `TransferEngine` calls `debit()` polymorphically without needing to know the concrete type."*

**Q5: "How did you serialize ReentrantLocks when saving binary snapshots?"**  
> **Answer:** *"ReentrantLocks represent OS-level thread synchronization state and cannot be written to disk. We marked `lock` as `transient` in `Account.java` and implemented a custom `readObject()` method that re-instantiates a fresh fair `ReentrantLock(true)` upon deserialization."*

**Q6: "How does `LedgerFileManager` export and import polymorphic accounts in CSV format?"**  
> **Answer:** *"The CSV exporter writes generic properties plus `Param1` and `Param2`. For SavingsAccount, these represent interest rate and minimum balance; for CreditAccount, credit limit and APR. When importing, `importAccountsFromCsv()` reads the `Type` column and invokes the matching subclass constructor."*

***

### 🔵 JAVAFX UI & TELEMETRY DASHBOARD (Muhammad Arslan)

**Q7: "Arslan, how did you update JavaFX UI components from background engine threads without thread crashes?"**  
> **Answer:** *"JavaFX controls can only be modified on the JavaFX Application Thread. We implemented `JavaFXEventBridge` using the Observer Pattern. When worker threads complete a transfer or detect fraud, the bridge wraps the callback inside `Platform.runLater()`, safely scheduling UI table updates onto the FX thread."*

**Q8: "How does the Telemetry Dashboard compute real-time TPS throughput and risk scores?"**  
> **Answer:** *"Timestamps are pushed onto a thread-safe `ConcurrentLinkedQueue<Long>`. `calculateCurrentTps(1000)` prunes timestamps older than 1 second to compute TPS in $O(1)$ time. Composite risk scores aggregate flagged and quarantined ratios, dynamically triggering threat badges (`NORMAL`, `ELEVATED`, `CRITICAL`)."*

***
