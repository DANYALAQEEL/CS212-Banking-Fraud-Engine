# DAY 1 COMPREHENSIVE ARCHITECTURAL EXPLANATION & VIVA GUIDE

**Project:** Automated Banking & Fraud Detection System  
**Phase:** Phase 1 — Domain Models & Data Foundations (Day 1 Milestone)  
**Assigned Commit Owners:** Danyal Aqeel (C01) & Hamza Zahoor (C02)  

---

## 1. What We Built & Why We Built It

### 1.1 Project Structure & Build Pipeline (`pom.xml`, `README.md`, `STATUS.md`)
* **What We Did:** Created a standard Maven Java 17 LTS project structure with JavaFX 17.0.2 and JUnit 5.9.2 dependencies.
* **Why We Did It:** 
  * Java 17 LTS provides modern language features like Java `record` (immutable data objects) and enhanced pattern matching.
  * Maven provides command-line build automation (`mvn clean compile`), ensuring the project can be built and verified on any examiner's machine without IDE coupling.
  * `STATUS.md` serves as a persistent dashboard tracking all 18 planned commits across the 12-day timeline.

### 1.2 Core Domain Models (`Account.java`, `Transaction.java`, `FraudResult.java`)
* **What We Did:** Built the foundational data classes for the entire application.
  * `Account.java`: Abstract base class for all bank account types.
  * `Transaction.java`: Immutable Java record representing financial transfer requests.
  * `FraudResult.java`: Immutable Java record representing rule evaluation tokens.
* **Why We Did It:**
  * Before writing concurrency locks or UI screens, we must define the core entity models and state contracts.
  * A bank account requires thread-safe balance visibility, a mutual exclusion lock for transfers, and a deterministic sorting order to prevent deadlocks.

---

## 2. Deep Breakdown of OOP Concepts Used

### Concept 1: Abstraction (Abstract Class `Account.java`)
* **Where Used:** `src/main/java/com/nust/banking/model/Account.java`
* **How We Used It:** Defined `public abstract class Account` with abstract method `public abstract void debit(double amount);`.
* **Why We Used It:** A generic "Account" cannot exist in a real bank—every account must be a specific type (Savings, Checking, Credit). Abstraction lets us define common state (ID, Owner, Balance, ReentrantLock) while forcing concrete subclasses to supply their own withdrawal/overdraft business rules.

### Concept 2: Encapsulation & Thread-Safe Memory Visibility
* **Where Used:** `Account.java` (`private final String id; private volatile double balance; private final ReentrantLock lock;`)
* **How We Used It:** All fields are strictly `private`. State modification occurs exclusively through `credit()`, `debit()`, and protected setters. The balance field is marked `volatile`.
* **Why We Used It:** Encapsulation prevents external classes from directly mutating account balances without validation. `volatile` guarantees *happens-before* memory visibility across CPU caches—when one thread updates balance, all other threads immediately observe the updated value on read queries without lock overhead.

### Concept 3: Immutability (Java `record Transaction` & `record FraudResult`)
* **Where Used:** `Transaction.java` and `FraudResult.java`
* **How We Used It:** Declared `public record Transaction(...)` and `public record FraudResult(...)`.
* **Why We Used It:** Records automatically make all fields `private final` and generate immutable getters, `equals()`, `hashCode()`, and `toString()`. In multi-threaded applications, **immutable objects are unconditionally thread-safe**—background worker threads and JavaFX UI threads can pass `Transaction` tokens freely without synchronization.

### Concept 4: Interface Implementation (`Comparable<Account>`)
* **Where Used:** `Account implements Comparable<Account>`
* **How We Used It:** Implemented `@Override public int compareTo(Account other) { return this.id.compareTo(other.id); }`.
* **Why We Used It:** Provides a natural lexicographical sorting order based on Account ID. This sorting order is strictly required by our concurrency engine (`AccountLockManager`) to acquire locks in ascending order, eliminating Coffman's *Circular Wait* condition and preventing deadlocks during multi-account transfers.

---

## 3. Method-by-Method Architectural Explanation

### `Account.java`
1. `Account(String id, String ownerName, double initialBalance)`
   * **What it does:** Constructor initializing account identity, owner, initial balance, and a fair `ReentrantLock(true)`.
   * **Why:** Validates inputs (non-null IDs, non-negative initial balance). Uses `new ReentrantLock(true)` to enforce a fair queueing policy that prevents thread starvation under high concurrency.
2. `getBalance()`
   * **What it does:** Unsynchronized point-in-time balance read.
   * **Why:** Reading a single `volatile` double is atomic in 64-bit JVMs and guarantees fresh memory visibility without incurring lock acquisition overhead.
3. `compareTo(Account other)`
   * **What it does:** Returns negative, zero, or positive integer comparing `this.id` to `other.id`.
   * **Why:** Used by `AccountLockManager` to sort locks deterministically before transfers.

### `Transaction.java`
1. `createNew(String fromId, String toId, double amount)`
   * **What it does:** Static factory method instantiating a new `Transaction` in `PENDING_PIPELINE` status with a unique UUID.
   * **Why:** Encapsulates transaction initialization logic cleanly.
2. `withStatus(TransactionStatus newStatus, String details)`
   * **What it does:** Creates a copy of the transaction with updated status while preserving immutability.
   * **Why:** Because `record` fields are `final`, mutating status requires returning a new record instance.

---

## 4. Viva Defense Q&A Master Sheet (Day 1)

**Q1: "Why did you choose an abstract class for Account instead of an interface?"**  
* **Answer:** *"An interface only defines method signatures, whereas an abstract class allows us to share both concrete state (`id`, `ownerName`, `balance`, `lock`) and concrete implementation (`credit()`, `compareTo()`) while still forcing subclasses to implement custom logic like `debit()`."*

**Q2: "Why is `compareTo` in `Account` critical for your concurrency engine?"**  
* **Answer:** *"To prevent deadlocks during transfers between Account A and Account B, threads must acquire locks in a deterministic global order. `compareTo` sorts accounts lexicographically by ID. Both threads lock the smaller ID first, breaking the circular wait condition."*

**Q3: "Why use Java records for transactions instead of standard POJOs with getters/setters?"**  
* **Answer:** *"Records guarantee immutability. Once a transaction record is created, its state cannot be modified by any thread. Immutable objects can be shared across background worker threads and the JavaFX thread safely without synchronization."*
