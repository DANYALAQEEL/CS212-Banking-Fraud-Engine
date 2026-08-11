package com.nust.banking.ui;

import com.nust.banking.engine.*;
import com.nust.banking.model.*;
import com.nust.banking.persistence.*;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

/**
 * JavaFX FXML Controller for the Main Banking & Fraud Engine Dashboard.
 * Handles UI component bindings, event dispatches to background worker threads,
 * live telemetry updates, file persistence, and strategy switching.
 *
 * @author Muhammad Arslan
 */
public class DashboardController implements Initializable, JavaFXEventListener {

    // --- Header Metrics ---
    @FXML private Label lblEngineStatus;
    @FXML private Label lblTotalLiquidity;
    @FXML private Label lblQueueDepth;
    @FXML private Label lblActiveThreads;

    // --- Telemetry Overlay Labels ---
    @FXML private Label lblTelemetryTps;
    @FXML private Label lblCompositeRisk;
    @FXML private Label lblThreatBadge;

    // --- Account Ledger Table Columns ---
    @FXML private TableView<Account> tblAccounts;
    @FXML private TableColumn<Account, String> colAccId;
    @FXML private TableColumn<Account, String> colAccOwner;
    @FXML private TableColumn<Account, String> colAccType;
    @FXML private TableColumn<Account, String> colAccBalance;
    @FXML private TableColumn<Account, String> colAccLockStatus;
    @FXML private Button btnRefreshLedger;
    @FXML private Button btnApplyInterest;

    // --- File Handling Action Buttons ---
    @FXML private Button btnSaveLedgerCsv;
    @FXML private Button btnLoadLedgerCsv;
    @FXML private Button btnSaveSnapshot;
    @FXML private Button btnLoadSnapshot;
    @FXML private Button btnExportAuditJson;

    // --- Control Panel ---
    @FXML private ComboBox<String> cmbSourceId;
    @FXML private ComboBox<String> cmbDestId;
    @FXML private TextField txtAmount;
    @FXML private ComboBox<LockStrategy.StrategyChoice> cmbLockStrategy;
    @FXML private Button btnExecuteTransfer;
    @FXML private Button btnInjectVelocity;
    @FXML private Button btnTriggerDeadlock;
    @FXML private Button btnResetSystem;
    @FXML private Label lblStatusMessage;

    // --- Live Transaction Stream Table Columns ---
    @FXML private TableView<Transaction> tblTransactions;
    @FXML private TableColumn<Transaction, String> colTxId;
    @FXML private TableColumn<Transaction, String> colTxFrom;
    @FXML private TableColumn<Transaction, String> colTxTo;
    @FXML private TableColumn<Transaction, String> colTxAmount;
    @FXML private TableColumn<Transaction, String> colTxStatus;
    @FXML private TableColumn<Transaction, String> colTxRisk;
    @FXML private TableColumn<Transaction, String> colTxDetails;

    // --- Engine Data Models & Infrastructure ---
    private final Map<String, Account> accountMap = new ConcurrentHashMap<>();
    private final ObservableList<Account> observableAccounts = FXCollections.observableArrayList();
    private final ObservableList<Transaction> observableTransactions = FXCollections.observableArrayList();

    private final TransferEngine transferEngine = new TransferEngine();
    private final FraudDetectionEngine fraudEngine = new FraudDetectionEngine();
    private final SettlementService settlementService = new SettlementService(transferEngine, fraudEngine);
    private final TelemetryViewController telemetryController = new TelemetryViewController();

    private final ThreadPoolExecutor workerThreadPool = new ThreadPoolExecutor(
        5, 5, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "settlement-worker");
            t.setDaemon(true);
            return t;
        }
    );

    private PipelineCoordinator pipelineCoordinator;
    private Timeline telemetryTimeline;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        setupStrategyDropdown();
        setupButtonHandlers();

        if (tblAccounts != null) {
            tblAccounts.setItems(observableAccounts);
        }
        if (tblTransactions != null) {
            tblTransactions.setItems(observableTransactions);
        }

        // Autoload ledger from data/ledger.csv if exists; else seed initial accounts
        File autoFile = new File("data/ledger.csv");
        if (autoFile.exists()) {
            try {
                List<Account> loaded = LedgerFileManager.importAccountsFromCsv(autoFile);
                accountMap.clear();
                for (Account acc : loaded) {
                    accountMap.put(acc.getId(), acc);
                }
                observableAccounts.setAll(accountMap.values());
            } catch (Exception e) {
                seedInitialAccounts();
            }
        } else {
            seedInitialAccounts();
        }

        setupAccountDropdowns();

        // Initialize PipelineCoordinator and register TelemetryViewController and self as listeners
        pipelineCoordinator = new PipelineCoordinator(settlementService, accountMap);
        pipelineCoordinator.addListener(telemetryController);
        pipelineCoordinator.addListener(this);

        // Start 1 Hz FX Timeline for telemetry metric refreshes
        telemetryTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateHeaderMetrics()));
        telemetryTimeline.setCycleCount(Timeline.INDEFINITE);
        telemetryTimeline.play();

        refreshLedgerView();
        setStatus("System Initialized. Select source and destination accounts to execute transfers.", "status-ok");
    }

    private void seedInitialAccounts() {
        accountMap.clear();

        SavingsAccount acc1  = new SavingsAccount("ACC-001", "Alice Smith", 500000.0, 0.05, 1000.0);
        CheckingAccount acc2 = new CheckingAccount("ACC-002", "Bob Jones", 250000.0, 5000.0, 25.0);
        CreditAccount acc3   = new CreditAccount("ACC-003", "Charlie Brown", 15000.0, 50000.0, 0.18);
        SavingsAccount acc4  = new SavingsAccount("ACC-004", "David Miller", 300000.0, 0.04, 1000.0);
        CheckingAccount acc5 = new CheckingAccount("ACC-005", "Emma Watson", 150000.0, 5000.0, 25.0);
        CreditAccount acc6   = new CreditAccount("ACC-006", "Frank Wright", 5000.0, 50000.0, 0.18);
        CheckingAccount bankFees = new CheckingAccount("BANK-FEES", "Institutional Fee Reserve", 0.0, 0.0, 0.0);

        accountMap.put(acc1.getId(), acc1);
        accountMap.put(acc2.getId(), acc2);
        accountMap.put(acc3.getId(), acc3);
        accountMap.put(acc4.getId(), acc4);
        accountMap.put(acc5.getId(), acc5);
        accountMap.put(acc6.getId(), acc6);
        accountMap.put(bankFees.getId(), bankFees);

        observableAccounts.setAll(accountMap.values());
        refreshLedgerView();
        setupAccountDropdowns();
    }

    private void setupTableColumns() {
        if (colAccId != null) colAccId.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getId()));
        if (colAccOwner != null) colAccOwner.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getOwnerName()));
        if (colAccType != null) colAccType.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getClass().getSimpleName()));
        if (colAccBalance != null) colAccBalance.setCellValueFactory(data -> new SimpleStringProperty(String.format(Locale.US, "RS %.2f", data.getValue().getBalance())));
        if (colAccLockStatus != null) colAccLockStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getLock().isLocked() ? "LOCKED" : "UNLOCKED"));

        if (colTxId != null) colTxId.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().transactionId()));
        if (colTxFrom != null) colTxFrom.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().fromAccountId()));
        if (colTxTo != null) colTxTo.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().toAccountId()));
        if (colTxAmount != null) colTxAmount.setCellValueFactory(data -> new SimpleStringProperty(String.format(Locale.US, "RS %.2f", data.getValue().amount())));
        if (colTxStatus != null) colTxStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status().name()));
        if (colTxRisk != null) colTxRisk.setCellValueFactory(data -> new SimpleStringProperty(String.format(Locale.US, "%.0f", 0.0)));
        if (colTxDetails != null) colTxDetails.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().statusDetails()));
    }

    private void setupAccountDropdowns() {
        ObservableList<String> accountIds = FXCollections.observableArrayList(accountMap.keySet());
        accountIds.remove("BANK-FEES");
        Collections.sort(accountIds);

        if (cmbSourceId != null) {
            cmbSourceId.setItems(accountIds);
            if (!accountIds.isEmpty()) cmbSourceId.getSelectionModel().select(0);
        }
        if (cmbDestId != null) {
            cmbDestId.setItems(accountIds);
            if (accountIds.size() > 1) cmbDestId.getSelectionModel().select(1);
        }
    }

    private void setupStrategyDropdown() {
        if (cmbLockStrategy != null) {
            cmbLockStrategy.setItems(FXCollections.observableArrayList(LockStrategy.StrategyChoice.values()));
            cmbLockStrategy.getSelectionModel().select(0);
        }
    }

    private void setupButtonHandlers() {
        if (btnExecuteTransfer != null) btnExecuteTransfer.setOnAction(e -> handleExecuteTransfer());
        if (btnInjectVelocity != null) btnInjectVelocity.setOnAction(e -> handleInjectTraffic());
        if (btnTriggerDeadlock != null) btnTriggerDeadlock.setOnAction(e -> handleTriggerDeadlock());
        if (btnResetSystem != null) btnResetSystem.setOnAction(e -> handleResetSystem());
        if (btnRefreshLedger != null) btnRefreshLedger.setOnAction(e -> refreshLedgerView());
        if (btnApplyInterest != null) btnApplyInterest.setOnAction(e -> handleApplyInterest());

        if (btnSaveLedgerCsv != null) btnSaveLedgerCsv.setOnAction(e -> handleSaveLedgerCsv());
        if (btnLoadLedgerCsv != null) btnLoadLedgerCsv.setOnAction(e -> handleLoadLedgerCsv());
        if (btnSaveSnapshot != null) btnSaveSnapshot.setOnAction(e -> handleSaveSnapshot());
        if (btnLoadSnapshot != null) btnLoadSnapshot.setOnAction(e -> handleLoadSnapshot());
        if (btnExportAuditJson != null) btnExportAuditJson.setOnAction(e -> handleExportAuditJson());
    }

    private void handleExecuteTransfer() {
        String srcId = cmbSourceId != null && cmbSourceId.getValue() != null ? cmbSourceId.getValue().trim() : "";
        String dstId = cmbDestId != null && cmbDestId.getValue() != null ? cmbDestId.getValue().trim() : "";
        String amtStr = txtAmount != null && txtAmount.getText() != null ? txtAmount.getText().trim() : "";

        if (srcId.isEmpty() || dstId.isEmpty() || amtStr.isEmpty()) {
            showAlert("Input Error", "Please select Source ID, Destination ID, and Amount.");
            return;
        }

        if (srcId.equalsIgnoreCase(dstId)) {
            showAlert("Invalid Transfer", "Source and Destination accounts cannot be identical.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amtStr);
            if (amount <= 0.0 || amount > 10_000_000.0) {
                showAlert("Input Error", "Transfer amount must be positive and not exceed RS 10,000,000.00.");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert("Input Error", "Invalid transfer amount format: " + amtStr);
            return;
        }

        Account src = accountMap.get(srcId);
        Account dst = accountMap.get(dstId);

        if (src == null || dst == null) {
            showAlert("Account Not Found", String.format("Selected accounts (%s -> %s) not found in ledger.", srcId, dstId));
            return;
        }

        LockStrategy.StrategyChoice choice = cmbLockStrategy != null ? cmbLockStrategy.getValue() : LockStrategy.StrategyChoice.SAFE;
        LockStrategy strategy = choice != null ? choice.getStrategy() : new LockStrategy.SafeLockStrategy();

        setStatus(String.format("Enqueuing transfer RS %.2f: %s -> %s [%s]...", amount, srcId, dstId, choice != null ? choice.getLabel() : "Safe"), "status-ok");

        workerThreadPool.submit(() -> {
            try {
                Transaction tx = Transaction.createNew(srcId, dstId, amount);
                boolean enqueued = pipelineCoordinator.submitTransaction(tx, 250, TimeUnit.MILLISECONDS);
                if (!enqueued) {
                    // Fallback to direct settlement if queue is full (backpressure)
                    Account feeAcc = accountMap.get("BANK-FEES");
                    SettlementService.SettlementOutcome outcome = settlementService.settle(src, dst, feeAcc, amount, strategy);
                    onTransactionProcessed(outcome.transaction(), outcome.fraudResult());
                    telemetryController.recordEvent(outcome.transaction(), outcome.fraudResult());
                }
            } catch (Exception e) {
                setStatus("Transfer submission error: " + e.getMessage(), "status-error");
            }
        });
    }

    private void handleInjectTraffic() {
        Account src = accountMap.get("ACC-001");
        Account dst = accountMap.get("ACC-002");

        if (src == null || dst == null) return;

        setStatus("INJECTING CONCURRENT TRAFFIC: Launching 7 simultaneous transfers...", "status-warn");

        CountDownLatch startGate = new CountDownLatch(1);
        for (int i = 0; i < 7; i++) {
            workerThreadPool.submit(() -> {
                try {
                    startGate.await();
                    Transaction tx = Transaction.createNew("ACC-001", "ACC-002", 1000.0);
                    pipelineCoordinator.submitTransaction(tx, 500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        startGate.countDown();
    }

    private void handleTriggerDeadlock() {
        Account accA = new SavingsAccount("DEMO-A", "Deadlock Demo A", 10000.0, 0.05, 1000.0);
        Account accB = new SavingsAccount("DEMO-B", "Deadlock Demo B", 10000.0, 0.05, 1000.0);
        LockStrategy naiveStrategy = new LockStrategy.DeadlockProneLockStrategy(150);

        setStatus("SIMULATING DEADLOCK: Launching opposing threads on demo accounts...", "status-error");

        Thread t1 = new Thread(() -> {
            try {
                accA.getLock().lockInterruptibly();
                try {
                    Thread.sleep(150);
                    accB.getLock().lockInterruptibly();
                    try {
                        accA.debit(100.0);
                        accB.credit(100.0);
                    } finally {
                        accB.getLock().unlock();
                    }
                } finally {
                    accA.getLock().unlock();
                }
            } catch (InterruptedException e) {
                // Deadlock broken by watchdog interrupt
            }
        }, "deadlock-demo-1");

        Thread t2 = new Thread(() -> {
            try {
                accB.getLock().lockInterruptibly();
                try {
                    Thread.sleep(150);
                    accA.getLock().lockInterruptibly();
                    try {
                        accB.debit(100.0);
                        accA.credit(100.0);
                    } finally {
                        accA.getLock().unlock();
                    }
                } finally {
                    accB.getLock().unlock();
                }
            } catch (InterruptedException e) {
                // Deadlock broken by watchdog interrupt
            }
        }, "deadlock-demo-2");

        t1.start();
        t2.start();

        // 800ms Watchdog using ThreadMXBean to detect and recover from deadlock
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            long[] deadlocked = bean.findDeadlockedThreads();
            if (deadlocked != null && deadlocked.length > 0) {
                t1.interrupt();
                t2.interrupt();
                setStatus(String.format("DEADLOCK DETECTED & RECOVERED: Watchdog interrupted %d deadlocked threads!", deadlocked.length), "status-warn");
                telemetryController.onDeadlockDetected("Watchdog interrupted Coffman Circular Wait deadlock");
            } else {
                setStatus("Deadlock demo completed.", "status-ok");
            }
        }, 800, TimeUnit.MILLISECONDS);
    }

    private void handleApplyInterest() {
        workerThreadPool.submit(() -> {
            int count = 0;
            for (Account acc : accountMap.values()) {
                if (acc instanceof SavingsAccount sav) {
                    AccountLockManager.executeWithLocks(sav, sav, sav::applyMonthlyInterest);
                    count++;
                }
            }
            final int appliedCount = count;
            Platform.runLater(() -> {
                refreshLedgerView();
                setStatus(String.format("Applied monthly interest to %d Savings Accounts.", appliedCount), "status-ok");
            });
        });
    }

    private void handleResetSystem() {
        seedInitialAccounts();
        observableTransactions.clear();
        fraudEngine.resetWindowHistory();
        telemetryController.resetTelemetry();
        if (lblEngineStatus != null) {
            lblEngineStatus.setText("ONLINE / RUNNING");
        }
        updateHeaderMetrics();
        setStatus("System Reset Complete: Initial seed balances restored.", "status-ok");
    }

    private void handleSaveLedgerCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Account Ledger CSV");
        fileChooser.setInitialFileName("ledger.csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            workerThreadPool.submit(() -> {
                try {
                    LedgerFileManager.exportAccountsToCsv(accountMap.values(), file);
                    setStatus("Ledger successfully exported to " + file.getName(), "status-ok");
                } catch (Exception e) {
                    setStatus("Failed to export ledger: " + e.getMessage(), "status-error");
                }
            });
        }
    }

    private void handleLoadLedgerCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Account Ledger CSV");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            workerThreadPool.submit(() -> {
                try {
                    List<Account> loaded = LedgerFileManager.importAccountsFromCsv(file);
                    accountMap.clear();
                    for (Account acc : loaded) {
                        accountMap.put(acc.getId(), acc);
                    }
                    Platform.runLater(() -> {
                        observableAccounts.setAll(accountMap.values());
                        setupAccountDropdowns();
                        refreshLedgerView();
                        setStatus("Ledger successfully loaded from " + file.getName(), "status-ok");
                    });
                } catch (Exception e) {
                    setStatus("Failed to load ledger: " + e.getMessage(), "status-error");
                }
            });
        }
    }

    private void handleSaveSnapshot() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Binary Engine Snapshot");
        fileChooser.setInitialFileName("snapshot.bin");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Binary Files (*.bin)", "*.bin"));
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            workerThreadPool.submit(() -> {
                try {
                    BinaryStateSerializer.EngineSnapshot snapshot = new BinaryStateSerializer.EngineSnapshot(accountMap.values(), observableTransactions);
                    BinaryStateSerializer.saveSnapshot(snapshot, file);
                    setStatus("Snapshot saved to " + file.getName(), "status-ok");
                } catch (Exception e) {
                    setStatus("Failed to save snapshot: " + e.getMessage(), "status-error");
                }
            });
        }
    }

    private void handleLoadSnapshot() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Binary Engine Snapshot");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Binary Files (*.bin)", "*.bin"));
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            workerThreadPool.submit(() -> {
                try {
                    BinaryStateSerializer.EngineSnapshot snapshot = BinaryStateSerializer.loadSnapshot(file);
                    accountMap.clear();
                    for (Account acc : snapshot.getAccounts()) {
                        accountMap.put(acc.getId(), acc);
                    }
                    Platform.runLater(() -> {
                        observableAccounts.setAll(accountMap.values());
                        observableTransactions.setAll(snapshot.getTransactions());
                        setupAccountDropdowns();
                        refreshLedgerView();
                        setStatus("Snapshot loaded from " + file.getName(), "status-ok");
                    });
                } catch (Exception e) {
                    setStatus("Failed to load snapshot: " + e.getMessage(), "status-error");
                }
            });
        }
    }

    private void handleExportAuditJson() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Transaction Audit JSON");
        fileChooser.setInitialFileName("audit_log.json");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            workerThreadPool.submit(() -> {
                try {
                    LedgerFileManager.exportTransactionsToJson(observableTransactions, file);
                    setStatus("Audit JSON exported to " + file.getName(), "status-ok");
                } catch (Exception e) {
                    setStatus("Failed to export JSON: " + e.getMessage(), "status-error");
                }
            });
        }
    }

    private void autosaveLedger() {
        try {
            File dir = new File("data");
            if (!dir.exists()) dir.mkdirs();
            LedgerFileManager.exportAccountsToCsv(accountMap.values(), new File(dir, "ledger.csv"));
        } catch (Exception ignored) {}
    }

    private void refreshLedgerView() {
        if (tblAccounts != null) tblAccounts.refresh();
        if (tblTransactions != null) tblTransactions.refresh();
        updateHeaderMetrics();
    }

    private void updateHeaderMetrics() {
        double netLiquidity = 0.0;
        for (Account acc : accountMap.values()) {
            netLiquidity += acc.getNetPosition();
        }
        if (lblTotalLiquidity != null) {
            lblTotalLiquidity.setText(String.format(Locale.US, "RS %.2f", netLiquidity));
        }
        if (lblQueueDepth != null && pipelineCoordinator != null) {
            lblQueueDepth.setText(pipelineCoordinator.getQueueSize() + " / 100");
        }
        if (lblActiveThreads != null) {
            lblActiveThreads.setText(workerThreadPool.getActiveCount() + " / " + workerThreadPool.getPoolSize() + " ACTIVE");
        }
        if (lblTelemetryTps != null) {
            lblTelemetryTps.setText(String.format(Locale.US, "TPS: %.1f", telemetryController.calculateCurrentTps(1000)));
        }
        if (lblCompositeRisk != null) {
            lblCompositeRisk.setText(String.format(Locale.US, "Risk: %.1f%%", telemetryController.calculateCompositeRiskScore()));
        }
        if (lblThreatBadge != null) {
            lblThreatBadge.setText(telemetryController.getThreatLevelBadge());
        }
    }

    private void setStatus(String message, String styleClass) {
        if (lblStatusMessage == null) return;
        Runnable task = () -> {
            lblStatusMessage.setText(message);
            lblStatusMessage.getStyleClass().removeAll("status-ok", "status-warn", "status-error");
            lblStatusMessage.getStyleClass().add(styleClass);
        };
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    @Override
    public void onTransactionProcessed(Transaction transaction, FraudResult fraudResult) {
        if (transaction != null) {
            Runnable task = () -> {
                observableTransactions.add(0, transaction);
                refreshLedgerView();
                if (fraudResult != null && fraudResult.status() != FraudResult.FraudStatus.CLEARED) {
                    setStatus(String.format("%s: Tx %s - %s (Risk: %.0f)",
                            fraudResult.status().name(), transaction.transactionId(), fraudResult.ruleTriggered(), fraudResult.riskScore()), "status-warn");
                } else {
                    setStatus(String.format("COMPLETED: Tx %s transferred RS %.2f", transaction.transactionId(), transaction.amount()), "status-ok");
                }
            };
            if (Platform.isFxApplicationThread()) {
                task.run();
            } else {
                Platform.runLater(task);
            }
        }
    }

    @Override
    public void onAccountBalanceChanged(Account account) {
        if (Platform.isFxApplicationThread()) {
            refreshLedgerView();
        } else {
            Platform.runLater(this::refreshLedgerView);
        }
    }

    @Override
    public void onDeadlockDetected(String details) {
        showAlert("Deadlock Warning", details);
    }

    private void showAlert(String title, String message) {
        Runnable task = () -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.show();
        };
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    public void shutdown() {
        autosaveLedger();
        if (telemetryTimeline != null) telemetryTimeline.stop();
        if (pipelineCoordinator != null) pipelineCoordinator.shutdown();
        workerThreadPool.shutdown();
        try {
            if (!workerThreadPool.awaitTermination(3, TimeUnit.SECONDS)) {
                workerThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
