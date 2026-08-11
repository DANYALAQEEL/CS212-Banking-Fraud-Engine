# DAY 2 COMPREHENSIVE ARCHITECTURAL EXPLANATION & VIVA GUIDE

**Project:** Automated Banking & Fraud Detection System  
**Phase:** Phase 1 — Account Subclasses & Unit Testing (Day 2 Milestone)  
**Assigned Commit Owner:** Hamza Zahoor (Commits C03 & C04)  

---

## 1. What We Built & Why We Built It

### 1.1 Account Subclasses (`SavingsAccount`, `CheckingAccount`, `CreditAccount`)
* **What We Did:** Implemented three specialized account subclasses extending `Account.java`:
  * `SavingsAccount.java`: Represents an interest-bearing savings account with minimum balance enforcement.
  * `CheckingAccount.java`: Represents a transactional checking account with overdraft limits and transaction fees.
  * `CreditAccount.java`: Represents a revolving credit card account tracking debt against a credit capacity limit.
* **Why We Did It:** 
  * Demonstrates real-world domain specialization and fulfills Category 2 of the rubric (**Flawless use of inheritance and polymorphism**).
  * Enforces business rules (minimum balance, overdraft allowance, fee deduction, credit debt limits) at the model level before state mutation occurs.

### 1.2 JUnit 5 Test Suite (`AccountTest.java`)
* **What We Did:** Created an automated test suite verifying subclass business rules and natural lock ordering.
* **Why We Did It:** 
  * Fulfills industry best practices (TDD/unit testing) and guarantees that business logic violations (e.g., withdrawing past minimum balance) throw predictable `IllegalStateException` errors.
  * Proves via automated assertion that `compareTo()` sorts account IDs lexicographically for deadlock prevention.

---

## 2. Deep Breakdown of OOP Concepts Used

### Concept 1: Inheritance (`extends Account`)
* **Where Used:** `SavingsAccount extends Account`, `CheckingAccount extends Account`, `CreditAccount extends Account`
* **How We Used It:** Each subclass inherits all common state (`id`, `ownerName`, `balance`, `lock`) and methods (`getBalance()`, `credit()`, `compareTo()`) from `Account.java`, while adding specific fields (`interestRate`, `minimumBalance`, `overdraftLimit`, `transactionFee`, `creditLimit`, `apr`).
* **Why We Used It:** Eliminates code duplication (DRY principle) and establishes an "IS-A" hierarchy (a SavingsAccount IS-A Bank Account).

### Concept 2: Polymorphism (Method Overriding `@Override public void debit(double amount)`)
* **Where Used:** Overridden `debit()` method in `SavingsAccount`, `CheckingAccount`, and `CreditAccount`.
* **How We Used It:**
  * `SavingsAccount.debit()`: Rejects debit if `getBalance() - amount < minimumBalance`.
  * `CheckingAccount.debit()`: Deducts `amount + transactionFee` and allows balance to drop to `-overdraftLimit`.
  * `CreditAccount.debit()`: Increases outstanding debt (`balance + amount`) up to `creditLimit`.
* **Why We Used It:** Allows the backend `TransferEngine` to process any `Account` polymorphically via `account.debit(amount)` without needing `if-else` or `instanceof` checks. The correct subclass behavior executes dynamically at runtime.

### Concept 3: Exception Handling & Defensive Guard Clauses
* **Where Used:** Constructors and `debit()` methods in all three subclasses.
* **How We Used It:** Used guard clauses throwing `IllegalArgumentException` (for invalid parameters like negative amounts) and `IllegalStateException` (for business rule violations like overdraft excess).
* **Why We Used It:** Prevents invalid domain state and ensures errors are caught early before modifying account balances.

---

## 3. Method-by-Method Architectural Explanation

### `SavingsAccount.java`
1. `SavingsAccount(id, ownerName, initialBalance, interestRate, minimumBalance)`
   * **What it does:** Validates that `initialBalance >= minimumBalance` and parameters are non-negative, then initializes fields.
   * **Why:** Prevents constructing a savings account that immediately violates its minimum balance rule.
2. `applyInterest()`
   * **What it does:** Calculates `interest = balance * interestRate` and calls `credit(interest)`.
   * **Why:** Encapsulates periodic interest calculation cleanly.
3. `debit(double amount)`
   * **What it does:** Checks `if (getBalance() - amount < minimumBalance)` before debiting.
   * **Why:** Enforces the minimum balance requirement, throwing `IllegalStateException` if violated.

### `CheckingAccount.java`
1. `debit(double amount)`
   * **What it does:** Computes `totalDeduction = amount + transactionFee` and verifies `getBalance() - totalDeduction >= -overdraftLimit`.
   * **Why:** Automatically charges transaction processing fees while honoring overdraft protection limits.

### `CreditAccount.java`
1. `getAvailableCredit()`
   * **What it does:** Returns `creditLimit - balance` (outstanding debt).
   * **Why:** Calculates remaining spending capacity dynamically.
2. `debit(double amount)`
   * **What it does:** Verifies `balance + amount <= creditLimit` and increases debt balance.
   * **Why:** Prevents credit card spending from exceeding the approved credit limit.

---

## 4. Viva Defense Q&A Master Sheet (Day 2)

**Q1: "Hamza, how do your account subclasses demonstrate OOP Polymorphism?"**  
* **Answer:** *"Each concrete subclass—SavingsAccount, CheckingAccount, and CreditAccount—inherits from the abstract Account base class and overrides the abstract `debit(double amount)` method. A polymorphic caller like TransferEngine can invoke `account.debit(amount)` on any Account reference without needing to know its specific runtime type, allowing each account to enforce its own unique rules dynamically."*

**Q2: "How does `CheckingAccount` handle overdraft protection and fees?"**  
* **Answer:** *"In `CheckingAccount.debit(amount)`, the total deduction equals `amount + transactionFee`. We verify that `getBalance() - totalDeduction >= -overdraftLimit`. This allows the account balance to go negative down to the overdraft boundary (e.g., -RS 5,000) while charging the fee, but rejects transactions that exceed the limit."*

**Q3: "Why does `CreditAccount` treat balance differently than Savings and Checking accounts?"**  
* **Answer:** *"In `CreditAccount`, the balance field represents outstanding debt rather than positive cash savings. Debits increase the debt balance up to the `creditLimit`, while credits pay down the debt balance. `getAvailableCredit()` calculates `creditLimit - balance`."*

**Q4: "How do your JUnit tests verify lock ordering for deadlock prevention?"**  
* **Answer:** *"Our unit test `testAccountCompareToLockOrdering()` instantiates accounts with IDs 'ACC-101', 'ACC-102', and 'ACC-103', and asserts that `acc101.compareTo(acc102) < 0`. This proves that our natural ordering algorithm deterministically sorts account IDs lexicographically before lock acquisition."*
