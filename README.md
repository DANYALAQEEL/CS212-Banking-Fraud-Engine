# Automated Banking & Fraud Detection Engine (v2.0 Final Release)

**Course:** CS-212 Object-Oriented Programming (Summer 2026, NUST SEECS)  
**Project Domain:** Automated High-Throughput Banking & Real-Time Fraud Detection Engine  
**GitHub Repository:** [https://github.com/DANYALAQEEL/CS212-Banking-Fraud-Engine](https://github.com/DANYALAQEEL/CS212-Banking-Fraud-Engine)  

---

## 👥 Team Roster
* **Danyal Aqeel** (`raqeel.bese24seecs@seecs.edu.pk`) — Lead Architect (Concurrency Engine & Lock Architecture)
* **Muhammad Arslan** (`arslan2147c@gmail.com`) — Senior Developer (JavaFX UI & Live Telemetry Dashboard)
* **Hamza Zahoor** (`hamzazahoor769@gmail.com`) — Junior Developer (Domain Models & File Persistence)

---

## ⚙️ System Requirements & Prerequisites
Before running the application, make sure your computer has:
1. **Java JDK 17 or higher** installed. (Download from [Oracle JDK 17](https://www.oracle.com/java/technologies/downloads/#java17) or [Eclipse Adoptium Temurin 17](https://adoptium.net/)).
2. **Apache Maven 3.8+** installed and added to your system `PATH` variable.
3. Minimum display resolution: **1280 × 800**.

---

## 🚀 How to Run the Application (Step-by-Step)

You can launch the **JavaFX Live Concurrency Dashboard** using any of the following 3 simple methods:

### Method 1: Desktop One-Click Launcher (Windows) — *Easiest*
1. Go to your **Windows Desktop**.
2. Double-click the file named **`RUN_BANKING_APP.bat`**.
3. A terminal window will open showing environment status, and the JavaFX GUI Dashboard will pop up immediately.
4. Keep the terminal window open while using the app (it logs live thread telemetry).

---

### Method 2: Command Line Launcher (Windows / macOS / Linux)

#### **On Windows (Command Prompt or PowerShell):**
1. Open Command Prompt (`cmd`) or PowerShell.
2. Navigate to the project directory:
   ```cmd
   cd C:\Users\Administrator\.gemini\antigravity\scratch\CS212-Banking-Fraud-Engine
   ```
3. Run the launcher script:
   ```cmd
   RUN_APP.bat
   ```

#### **On macOS / Linux:**
1. Open your Terminal application.
2. Navigate to the project directory:
   ```bash
   cd /path/to/CS212-Banking-Fraud-Engine
   ```
3. Grant execute permissions (first time only) and run:
   ```bash
   chmod +x run_app.sh run_tests.sh
   ./run_app.sh
   ```

---

### Method 3: Standard Maven Command (Any Platform / IDE Terminal)
Open any terminal inside the project directory and run:
```bash
mvn javafx:run
```

---

### Method 4: Running Inside IntelliJ IDEA / Eclipse / VS Code
1. Open your IDE and select **Open Project** $\rightarrow$ choose the [`CS212-Banking-Fraud-Engine`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine) directory.
2. Allow Maven to import dependencies automatically (`pom.xml`).
3. Navigate to `src/main/java/com/nust/banking/ui/MainApp.java`.
4. Right-click `MainApp.java` and click **Run 'MainApp.main()'**.

---

## 🧪 How to Run the Automated Test Suite (55 Tests)

To verify system correctness, test concurrent transfers, fraud rules, and ledger persistence:

#### **On Windows:**
Double-click [`RUN_TESTS.bat`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/RUN_TESTS.bat) or run in Command Prompt:
```cmd
RUN_TESTS.bat
```

#### **On macOS / Linux:**
```bash
./run_tests.sh
```

#### **Using Maven:**
```bash
mvn test
```

---

## 🎯 Detailed Guide to Using the Dashboard Features

Once the application launches, you will see a dark-themed fintech dashboard with two main split panels:

### 1. Left Panel — Account Ledger (`tblAccounts`)
* **Live Balances:** Displays initial accounts (`ACC-001` to `ACC-006` and `BANK-FEES`) with exact balances in PKR.
* **Apply Monthly Interest Button:** Click to calculate and credit monthly interest to all `SavingsAccount` instances.
* **Refresh Ledger Button:** Force-refreshes account rows and total system liquidity metrics.
* **Save/Load Ledger (CSV):** Export or import the current account state to/from a CSV file (`data/ledger.csv`).
* **Save/Load Snapshot:** Save or restore binary engine state snapshots (`.bin` files).

### 2. Right Panel — Control & Strategy Panel
* **Source & Destination Account Selectors:** Choose source account (e.g. `ACC-006`) and target account (e.g. `ACC-005`).
* **Transfer Amount:** Enter desired transfer amount (e.g. `5000.00`).
* **Lock Strategy Dropdown:** Select execution strategy:
  - `Deterministic Safe Strategy (Sorted ID)` — Guaranteed deadlock-free $N$-account lock ordering.
  - `Naive Deadlock-Prone Strategy` — Educational demo strategy highlighting Coffman circular waits.
  - `Timed Lock Strategy (Backoff)` — Acquires locks with timeout and exponential backoff retry.
* **Execute Transfer Button:** Submits transaction to the pre-execution fraud settlement pipeline.
* **Inject Traffic Button:** Launches 7 concurrent threads performing simultaneous transfers to stress-test queue depth and lock safety.
* **Simulate Deadlock Button:** Triggers intentional circular deadlock on isolated demo threads. An 800ms background JMX watchdog detects the deadlock and automatically resolves it via thread interruption.
* **Export Audit (JSON):** Export complete transaction history with risk scores and fraud verdicts to JSON format.
* **Reset System Button:** Clears transaction history and restores default initial account balances.

---

## 🏗️ Architectural Guarantees & Highlights
1. **Pre-Execution Fraud Settlement Pipeline (`SettlementService`):** Evaluates risk scores and rules *before* settling transfers. Quarantined transactions ($\ge 70$ risk score) are blocked with status `FLAGGED_FRAUD` leaving account balances untouched.
2. **Deterministic Multi-Account Lock Ordering:** `AccountLockManager.executeWithLocks()` sorts accounts lexicographically by Account ID (`compareTo`), breaking Coffman's Circular Wait condition across arbitrary $N$ accounts.
3. **Institutional Net Position Invariant:** Polymorphic `Account.getNetPosition()` returns signed liquidity contribution (Savings & Checking assets positive, Credit debt liabilities negative). Total net position remains invariant across all transfers including fee routing.
4. **Thread-Safe Sliding Window Tracking:** `FraudDetectionEngine` evaluates Rapid Drain on pre-transfer balance (firing at $\ge 90\%$) and tracks velocity sliding windows using per-key synchronized `ArrayDeque` monitors with automatic pruning.
5. **Active Bounded Backpressure (`PipelineCoordinator`):** Dual `Condition` variables (`notFull`, `notEmpty`) manage a bounded 100-item `TransactionPipelineQueue` with 3 dedicated worker consumer threads publishing UI events safely via `Platform.runLater()`.
6. **Recoverable Deadlock Watchdog:** An 800ms JMX background watchdog detects deadlocked threads via `ThreadMXBean` and breaks deadlocks safely via thread interruption.

---

## 📑 Project Artifacts & Reports
* **[`FINAL_REPORT.md`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/FINAL_REPORT.md):** Complete single-pass audit remediation report, mathematical proofs, benchmark results, and verification matrix.
* **[`UML_v2.pdf`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/UML_v2.pdf):** Rendered vector class diagram matching post-remediation release.
* **[`docs/UML_v2.md`](file:///C:/Users/Administrator/.gemini/antigravity/scratch/CS212-Banking-Fraud-Engine/docs/UML_v2.md):** Evolved Mermaid class diagram source.
