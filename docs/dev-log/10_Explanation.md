# Day 10 Architectural Breakdown & Viva Defense Guide

**Author:** Hamza Zahoor (`hzahoor.bsai25seecs@seecs.edu.pk`)  
**Component:** Phase 5 — File Persistence & Binary State Serialization  
**Commit Range:** C15 & C16  
**Date:** August 03, 2026  

---

## 1. Executive Summary & Why We Built This

On Day 10, we implemented **`LedgerFileManager.java`** and **`BinaryStateSerializer.java`** to provide multi-format storage capabilities for our core domain entities and audit streams.

### Key Architectural Challenge: Polymorphic File Storage & Transient Locks
1. **Polymorphic CSV Restoration:** Account ledgers contain polymorphic subclasses (`SavingsAccount`, `CheckingAccount`, `CreditAccount`), each requiring distinct constructor arguments (e.g., interest rate, overdraft limit, or APR).
2. **Binary Deserialization Locks:** Java `ReentrantLock` instances are non-serializable operating-system synchronization primitives. When snapshotting `Account` objects via `ObjectOutputStream`, locks must be declared `transient` and safely re-instantiated upon deserialization inside `readObject(ObjectInputStream)`.

---

## 2. Technical Implementation Highlights

### A. CSV & JSON Storage (`LedgerFileManager.java`)
* **CSV Account Ledger Export/Import:** Exports accounts to a structured CSV format (`ID,Type,OwnerName,Balance,Param1,Param2`). When reading CSV files, `importAccountsFromCsv()` dynamically instantiates `SavingsAccount`, `CheckingAccount`, or `CreditAccount` objects based on the `Type` column.
* **JSON Audit Stream:** Writes formatted JSON transaction arrays containing timestamp ISO strings, status codes, and fraud evaluation details.

### B. Binary State Snapshotting (`BinaryStateSerializer.java`)
* **Object Stream Snapshotting:** Uses `ObjectOutputStream` and `ObjectInputStream` to save/restore `EngineSnapshot` records containing account lists, transaction history, and system metadata.
* **`readObject()` Custom Deserialization:**
  ```java
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      this.lock = new ReentrantLock(true); // Re-initialize fair lock instance
  }
  ```

---

## 3. OOP Concepts & Design Patterns Applied

| OOP Concept / Pattern | Application in Day 10 Code |
| :--- | :--- |
| **Polymorphism** | `LedgerFileManager` dynamically reconstructs subclass instances (`SavingsAccount`, `CheckingAccount`, `CreditAccount`) from generic `Account` CSV lines. |
| **Transient Synchronization** | Declared `ReentrantLock` as `transient` inside `Account.java` to decouple concurrency lock states from file persistence data. |
| **Record Immutability** | `EngineSnapshot` and `Transaction` records implement `Serializable` while guaranteeing thread-safe value semantics. |

---

## 4. Day 10 Viva Defense Q&A (WhatsApp Study Notes)

### Q1: "Hamza, how did you handle deserializing ReentrantLocks inside Account objects during binary snapshot loading?"
> **Answer:** *"ReentrantLocks represent OS-level thread synchronization state and cannot be serialized directly into a file. We marked `lock` as `transient` in `Account.java` and overrode `readObject(ObjectInputStream in)`. When Java deserializes an Account from disk, `readObject` automatically re-instantiates a new fair `ReentrantLock(true)` instance."*

### Q2: "How does CSV import preserve the specific properties of Savings, Checking, and Credit accounts?"
> **Answer:** *"The CSV header contains `Param1` and `Param2`. During export, a SavingsAccount writes its interest rate and minimum balance, whereas a CreditAccount writes its credit limit and APR. During import, `importAccountsFromCsv` reads the `Type` column and invokes the correct subclass constructor."*
