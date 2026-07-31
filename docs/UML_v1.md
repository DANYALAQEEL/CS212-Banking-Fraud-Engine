# INITIAL ARCHITECTURAL CLASS DIAGRAM (UML v1.0)

**Project:** Automated Banking & Fraud Detection System  
**Phase:** Phase 1 & 2 Core Class Structure (Day 5 Milestone)  
**Assigned Commit Owner:** Hamza Zahoor (Commit C05)  

---

## 1. Class Diagram Overview

```mermaid
classDiagram
    class Account {
        <<abstract>>
        #String id
        #String accountHolderName
        #volatile double balance
        #ReentrantLock lock
        +getId() String
        +getAccountHolderName() String
        +getBalance() double
        +getLock() ReentrantLock
        +credit(double amount)* void
        +debit(double amount)* void
        +compareTo(Account other) int
    }

    class SavingsAccount {
        -double interestRate
        -static double MINIMUM_BALANCE
        +credit(double amount) void
        +debit(double amount) void
        +applyInterest() void
    }

    class CheckingAccount {
        -static double OVERDRAFT_LIMIT
        -static double TRANSACTION_FEE
        +credit(double amount) void
        +debit(double amount) void
    }

    class CreditAccount {
        -double creditLimit
        -double interestRate
        +credit(double amount) void
        +debit(double amount) void
        +calculateMonthlyInterest() double
    }

    class Transaction {
        <<record>>
        +String id()
        +String fromAccountId()
        +String toAccountId()
        +double amount()
        +Instant timestamp()
        +TransactionStatus status()
        +String message()
        +withStatus(TransactionStatus, String) Transaction
    }

    class FraudResult {
        <<record>>
        +String transactionId()
        +int riskScore()
        +FraudStatus status()
        +String reason()
        +Instant evaluatedAt()
        +isCleared() boolean
    }

    class AccountLockManager {
        <<utility>>
        +executeWithLocks(Account acc1, Account acc2, Runnable action)$ void
    }

    class TransferEngine {
        +processTransfer(Account from, Account to, double amount) Transaction
    }

    class TransactionPipelineQueue {
        -int capacity
        -PriorityQueue~Transaction~ queue
        -ReentrantLock lock
        -Condition notFull
        -Condition notEmpty
        +put(Transaction tx) void
        +take() Transaction
        +shutdown() void
    }

    class FraudDetectionEngine {
        -Map~String, List~Instant~~ sourceVelocityMap
        -Map~String, List~Transaction~~ targetPatternMap
        +evaluateTransaction(Transaction tx, Account sourceAcc) FraudResult
    }

    Account <|-- SavingsAccount
    Account <|-- CheckingAccount
    Account <|-- CreditAccount
    TransferEngine ..> AccountLockManager : uses
    TransferEngine ..> Account : locks & mutates
    TransferEngine ..> Transaction : produces
    TransactionPipelineQueue "1" *-- "0..*" Transaction : buffers
    FraudDetectionEngine ..> Transaction : evaluates
    FraudDetectionEngine ..> FraudResult : produces
```

---

## 2. Key Architecture Relationships Highlighted

1. **Polymorphic Inheritance:** `SavingsAccount`, `CheckingAccount`, and `CreditAccount` extend the abstract `Account` base class, implementing specialized `debit()` balance protection rules.
2. **Lock Order Coupling:** `AccountLockManager` enforces total lock ordering on pairs of `Account` locks prior to execution inside `TransferEngine`.
3. **Producer-Consumer Handoff:** `TransactionPipelineQueue` holds buffered `Transaction` records awaiting `FraudDetectionEngine` analysis.
