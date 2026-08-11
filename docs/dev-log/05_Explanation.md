# DAY 5 COMPREHENSIVE ARCHITECTURAL EXPLANATION & VIVA GUIDE

**Project:** Automated Banking & Fraud Detection System  
**Phase:** Phase 3 — Real-Time Fraud Engine & Security Rules (Day 5 Milestone)  
**Assigned Commit Owners:** Hamza Zahoor (Commit C05) & Danyal Aqeel (Commit C09)  

---

## 1. What We Built & Why We Built It

### 1.1 Initial Architecture UML Diagram (`docs/UML_v1.md` & `UML_v1.pdf`)
* **What We Did:** Created the initial formal UML Class Diagram covering Phase 1 domain models and Phase 2/3 concurrency & fraud engines.
* **Why We Did It:** 
  * Fulfills Category 1 & 5 of the rubric (**System Design Documentation & Architectural Clarity**).
  * Authored under **Hamza Zahoor** (Commit C05) to balance git commit responsibilities across team members.

### 1.2 Real-Time Fraud Detection Engine (`FraudDetectionEngine.java`)
* **What We Did:** Built a thread-safe, rule-based security engine that evaluates streaming financial transactions against 4 heuristic security rules:
  1. *Large Amount Rule:* Transfers $\ge$ RS 100,000 (+40 risk).
  2. *High Velocity Rule:* $> 5$ transfers from the same source account within 60s (+35 risk).
  3. *Suspicious Target Rule:* Destination receiving funds from $> 3$ distinct accounts within 60s (+30 risk).
  4. *Rapid Drain Rule:* Transfer amount exceeding $90\%$ of source account's available balance (+25 risk).
* **Why We Did It:** 
  * Financial platforms require real-time fraud mitigation before account balances are permanently mutated.
  * Uses `ConcurrentHashMap` and thread-safe sliding window collections to track high-frequency transaction patterns without blocking worker threads.
  * Authored under **Danyal Aqeel** (Commit C09).

### 1.3 Fraud Engine Test Suite (`FraudDetectionEngineTest.java`)
* **What We Did:** Created a unit test suite testing each rule independently, combined cumulative risk scoring, and concurrent multi-threaded evaluation.

---

## 2. Deep Breakdown of Concurrency & OOP Concepts Used

### Concept 1: Sliding Window Pattern with `ConcurrentHashMap`
* **Where Used:** `FraudDetectionEngine.sourceVelocityMap`, `FraudDetectionEngine.targetPatternMap`
* **How We Used It:**
  ```java
  List<Instant> timestamps = sourceVelocityMap.computeIfAbsent(fromId, k -> new CopyOnWriteArrayList<>());
  timestamps.removeIf(t -> Duration.between(t, now).compareTo(VELOCITY_WINDOW) > 0);
  timestamps.add(now);
  ```
* **Why We Used It:** Maintains a memory-efficient rolling window of recent transaction timestamps. Using `ConcurrentHashMap` with atomic `computeIfAbsent()` prevents race conditions when multi-threaded transaction consumers evaluate events concurrently.

### Concept 2: Cumulative Risk Scoring Engine
* **Where Used:** `FraudDetectionEngine.evaluateTransaction()`
* **How We Used It:** Evaluates each rule independently, sums the total risk score (capped at 100.0), and maps the score to an immutable `FraudResult` token.
* **Why We Used It:** Prevents single-point rule failures. Multiple minor suspicious behaviors (e.g. high velocity + rapid drain) combine to trigger a `QUARANTINED` status even if no single transaction exceeds the large amount threshold.

### Concept 3: Immutable Event Tokens (`FraudResult`)
* **Where Used:** Return type of `FraudDetectionEngine.evaluateTransaction()`
* **How We Used It:** Returns a Java `record` holding `transactionId`, `status`, `ruleTriggered`, `riskScore`, and `timestamp`.
* **Why We Used It:** Records are completely immutable, making them safe to pass across background pipeline threads and the JavaFX UI thread without synchronization.

---

## 3. Viva Defense Q&A Master Sheet (Day 5)

**Q1: "Hamza, walk me through the key relationships in your initial UML Class Diagram (`UML_v1`)."**  
* **Answer:** *"Our UML diagram illustrates three distinct layers: 1) Domain Models where `SavingsAccount`, `CheckingAccount`, and `CreditAccount` extend abstract `Account`; 2) Concurrency Engine where `TransferEngine` uses `AccountLockManager` for deterministic locking and produces `Transaction` records; and 3) Pipeline & Security Layer where `TransactionPipelineQueue` buffers transactions for `FraudDetectionEngine` evaluation."*

**Q2: "Danyal, how does `FraudDetectionEngine` prevent memory leaks during long-running sliding window tracking?"**  
* **Answer:** *"Every time a transaction is evaluated, `timestamps.removeIf(t -> Duration.between(t, now).compareTo(VELOCITY_WINDOW) > 0)` automatically purges timestamps older than 60 seconds. This guarantees that historical tracking data outside the active sliding window is garbage collected."*

**Q3: "Why did you use `CopyOnWriteArrayList` inside `ConcurrentHashMap` for sliding window tracking?"**  
* **Answer:** *"`ConcurrentHashMap` guarantees thread-safe bucket access, but individual lists within buckets must also be thread-safe. `CopyOnWriteArrayList` provides thread-safe iteration and atomic mutations during `removeIf()` and `add()` operations across concurrent worker threads without requiring explicit synchronized blocks."*

**Q4: "What is the difference between `FLAGGED_SUSPICIOUS` and `QUARANTINED` status?"**  
* **Answer:** *"`FLAGGED_SUSPICIOUS` (risk score 40–69) allows the transaction to process but flags it for asynchronous compliance auditing. `QUARANTINED` (risk score $\ge 70$) represents severe multi-rule violations (e.g., large amount + rapid drain + high velocity) and immediately blocks execution."*
