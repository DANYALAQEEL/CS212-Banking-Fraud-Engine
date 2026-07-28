# Automated Banking & Fraud Detection System

**Course:** CS-212 Object-Oriented Programming (Summer 2026, NUST SEECS)  
**Project Domain:** Automated Banking & Fraud Detection System  

## Team Members
* **Danyal Aqeel** (Lead — Concurrency Engine & Lock Architecture)
* **Muhammad Arslan** (Senior — JavaFX UI & Thread Telemetry Dashboard)
* **Hamza Zahoor** (Junior — Domain Models & File Persistence)

## Architecture Overview
This system is a high-throughput, concurrent financial transaction engine designed to execute parallel multi-account transfers without race conditions or deadlocks. It incorporates an asynchronous pipeline that routes pending transactions through worker threads executing pluggable fraud detection rules before state modification.

### Key Technical Highlights
1. **Deterministic Lock Ordering:** Eliminates circular wait deadlocks during concurrent multi-account transfers by acquiring locks in strict numerical ID order (`AccountLockManager`).
2. **Pre-Execution Fraud Pipeline:** Asynchronously filters transactions through a `TransactionPipelineQueue` consumed by a `FraudDetectionEngine` worker pool.
3. **TOCTOU Prevention:** Atomic re-validation inside the lock boundary closes the Time-of-Check to Time-of-Use window.
4. **Thread Telemetry Dashboard:** JavaFX view rendering real-time thread activity, queue capacity, and execution latency safely via `Platform.runLater()`.
5. **Permanent Persistence:** CSV ledgers and binary state snapshots restored seamlessly upon startup.

## Quickstart
### Prerequisites
* Java JDK 17+
* Maven 3.8+

### Build & Run
```bash
# Compile the project
mvn clean compile

# Run unit tests
mvn test

# Launch the JavaFX Application
mvn javafx:run
```
