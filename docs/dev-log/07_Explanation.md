# DAY 7 COMPREHENSIVE ARCHITECTURAL EXPLANATION & VIVA GUIDE

**Project:** Automated Banking & Fraud Detection System  
**Phase:** Phase 4 — JavaFX Dashboard & UI Shell (Day 7 Milestone)  
**Assigned Commit Owner:** Muhammad Arslan (Commit C11)  

---

## 1. What We Built & Why We Built It

### 1.1 JavaFX Application Entry Point (`MainApp.java`)
* **What We Did:** Created the primary JavaFX Application class extending `javafx.application.Application`.
* **Why We Did It:** 
  * Fulfills Category 5 & 6 of the rubric (**JavaFX Desktop UI Architecture & Thread Management**).
  * Authored under **Muhammad Arslan** (`arslan2147c@gmail.com`) for Commit C11 to fulfill team contribution balance.
  * Configures window dimensions (1280x800, min 1024x700), loads FXML views, injects CSS stylesheets, and hooks into `stop()` for clean thread pool shutdown when the window is closed.

### 1.2 Dashboard FXML Layout (`main_dashboard.fxml`)
* **What We Did:** Designed a rich, dark-mode FXML user interface featuring:
  1. *Header Telemetry Bar:* Live Engine Status (`RUNNING`), Total Liquidity Balance (`RS 1,250,000.00`), Queue Depth (`0/100`), and Active Thread Count (`4 ACTIVE`).
  2. *Account Ledger Table:* `TableView<Account>` showing Account ID, Owner Name, Subclass Type, Balance, and Lock State.
  3. *Transfer Control Panel:* Interactive form with Source ID, Destination ID, Amount, Strategy ChoiceBox (`Deterministic Safe`, `Naive Deadlock-Prone`, `Timed Backoff`), and action buttons (`Execute Transfer`, `Inject Traffic`, `Simulate Deadlock`, `Reset System`).
  4. *Live Transaction Stream Table:* `TableView<Transaction>` displaying streaming settlement transactions and real-time fraud analysis messages.

### 1.3 Dark-Mode CSS Design Tokens (`styles.css`)
* **What We Did:** Created a modern custom CSS design system using dark Slate tokens (`#0f172a`, `#1e293b`), Cyan highlights (`#38bdf8`), Emerald success indicators (`#4ade80`), and Rose alert accents (`#dc2626`).

---

## 2. Deep Breakdown of Concurrency & OOP Concepts Used

### Concept 1: JavaFX Application Lifecycle Management
* **Where Used:** `MainApp.start(Stage stage)`, `MainApp.stop()`
* **How We Used It:**
  ```java
  stage.setOnCloseRequest(event -> stop());
  ```
* **Why We Used It:** JavaFX operates on a specialized JavaFX Application Thread. Overriding `stop()` ensures that when the user closes the GUI window, background worker thread pools (`ExecutorService` and `TransactionPipelineQueue`) are signaled to terminate, preventing orphan threads from hanging in memory.

### Concept 2: MVC (Model-View-Controller) Architecture Decoupling
* **Where Used:** `main_dashboard.fxml` view separation from domain models (`Account`, `Transaction`).
* **How We Used It:** Defined FXML view components declaratively using XML nodes (`TableView`, `TableColumn`, `GridPane`), decoupling visual layout from domain models and controller logic.
* **Why We Used It:** Follows strict SE separation of concerns. UI layouts can be customized or restyled without modifying backend transaction execution code.

---

## 3. Viva Defense Q&A Master Sheet (Day 7)

**Q1: "Arslan, walk me through the lifecycle of a JavaFX Application in `MainApp.java`."**  
* **Answer:** *"The JavaFX launcher invokes `main()`, which calls `launch()`. The framework initializes the JavaFX runtime, calls `init()`, and then invokes `start(Stage stage)` on the JavaFX Application Thread. `start()` loads `main_dashboard.fxml`, attaches `styles.css`, and displays the stage. When the user closes the window, JavaFX automatically invokes `stop()`, where we execute clean thread pool tear-down."*

**Q2: "Why is it dangerous to perform backend transaction transfers directly inside JavaFX event handlers?"**  
* **Answer:** *"JavaFX UI updates run exclusively on a single thread called the JavaFX Application Thread. If a button handler performs a long-running transfer or waits on an `AccountLockManager` lock directly on the UI thread, the entire GUI freezes (Not Responding). We must dispatch transfers to background worker threads and push status updates back to JavaFX via `Platform.runLater()`."*

**Q3: "How does `main_dashboard.fxml` support dynamic strategy selection during transfers?"**  
* **Answer:** *"The control panel includes a `ComboBox<String>` (`cmbLockStrategy`). The user selects between 'Deterministic Safe', 'Naive Deadlock-Prone', and 'Timed Backoff'. When a transfer is submitted, the controller reads the selected value and passes the corresponding `LockStrategy` implementation (`SafeLockStrategy`, `DeadlockProneLockStrategy`, or `TimedLockStrategy`) to the engine."*
