package com.nust.banking.ui;

import com.nust.banking.engine.*;
import com.nust.banking.model.*;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

/**
 * JavaFX FXML Controller for the Main Banking & Fraud Engine Dashboard.
 *
 * <p>Handles UI component bindings, event dispatches to background worker threads,
 * live telemetry updates, and strategy switching between safe, deadlock-prone, and timed locks.
 *
 * @author Muhammad Arslan
 */
public class DashboardController implements Initializable, JavaFXEventListener {

    // --- Header Metrics ---
    @FXML private Label lblEngineStatus;
    @FXML private Label lblTotalLiquidity;
    @FXML private Label lblQueueDepth;
    @FXML private Label lblActiveThreads;

    // --- Account Ledger Table ---
    @FXML private TableView<Account> tblAccounts;
    @FXML private TableColumn<Account, String> colAccId;
    @FXML private TableColumn<Account, String> colAccOwner;
    @FXML private TableColumn<Account, String> colAccType;
    @FXML private TableColumn<Account, String> colAccBalance;
    @FXML private TableColumn<Account, String> colAccLockStatus;
    @FXML private Button btnRefreshLedger;

    // --- Control Panel ---
    @FXML private TextField txtSourceId;
    @FXML private TextField txtDestId;
    @FXML private TextField txtAmount;
    @FXML private ComboBox<String> cmbLockStrategy;
    @FXML private Button btnExecuteTransfer;
    @FXML private Button btnInjectVelocity;
    @FXML private Button btnTriggerDeadlock;
    @FXML private Button btnResetSystem;

    // --- Live Transaction Stream Table ---
    @FXML private TableView<Transaction> tblTransactions;
    @FXML private TableColumn<Transaction, String> colTxId;
    @FXML private TableColumn<Transaction, String> colTxFrom;
    @FXML private TableColumn<Transaction, String> colTxTo;
    @FXML private TableColumn<Transaction, String> colTxAmount;
    @FXML private TableColumn<Transaction, String> colTxStatus;
    @FXML private TableColumn<Transaction, String> colTxDetails;

    // --- Engine Data Models ---
    private final Map<String, Account> accountMap = new ConcurrentHashMap<>();
    private final ObservableList<Account> observableAccounts = FXCollections.observableArrayList();
    private final ObservableList<Transaction> observableTransactions = FXCollections.observableArrayList();

    private final TransferEngine transferEngine = new TransferEngine();
    private final FraudDetectionEngine fraudEngine = new FraudDetectionEngine();
    private final TransactionPipelineQueue pipelineQueue = new TransactionPipelineQueue(100);
    private final ExecutorService workerThreadPool = Executors.newFixedThreadPool(5);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        seedInitialAccounts();
        setupTableColumns();
        setupStrategyDropdown();
        setupButtonHandlers();

        updateHeaderMetrics();
    }

    private void seedInitialAccounts() {
        accountMap.clear();
        observableAccounts.clear();

        SavingsAccount acc1  = new SavingsAccount("ACC-001", "Alice Smith", 500000.0, 0.05, 1000.0);
        CheckingAccount acc2 = new CheckingAccount("ACC-002", "Bob Jones", 250000.0, 5000.0, 25.0);
        CreditAccount acc3   = new CreditAccount("ACC-003", "Charlie Brown", -15000.0, 50000.0, 0.18);
        SavingsAccount acc4  = new SavingsAccount("ACC-004", "David Miller", 300000.0, 0.04, 1000.0);
        CheckingAccount acc5 = new CheckingAccount("ACC-005", "Emma Watson", 150000.0, 5000.0, 25.0);
        CreditAccount acc6   = new CreditAccount("ACC-006", "Frank Wright", -5000.0, 50000.0, 0.18);

        accountMap.put(acc1.getId(), acc1);
        accountMap.put(acc2.getId(), acc2);
        accountMap.put(acc3.getId(), acc3);
        accountMap.put(acc4.getId(), acc4);
        accountMap.put(acc5.getId(), acc5);
        accountMap.put(acc6.getId(), acc6);

        observableAccounts.addAll(accountMap.values());
        if (tblAccounts != null) {
            tblAccounts.setItems(observableAccounts);
        }
        if (tblTransactions != null) {
            tblTransactions.setItems(observableTransactions);
        }
    }

    private void setupTableColumns() {
        if (colAccId == null) return;

        // Account Ledger Columns
        colAccId.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getId()));
        colAccOwner.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getOwnerName()));
        colAccType.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getClass().getSimpleName()));
        colAccBalance.setCellValueFactory(data -> new SimpleStringProperty(
                String.format("RS %.2f", data.getValue().getBalance())));
        colAccLockStatus.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getLock().isLocked() ? "LOCKED" : "UNLOCKED"));

        // Transaction Table Columns
        colTxId.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().transactionId()));
        colTxFrom.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().fromAccountId()));
        colTxTo.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().toAccountId()));
        colTxAmount.setCellValueFactory(data -> new SimpleStringProperty(
                String.format("RS %.2f", data.getValue().amount())));
        colTxStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status().name()));
        colTxDetails.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().statusDetails()));
    }

    private void setupStrategyDropdown() {
        if (cmbLockStrategy != null) {
            cmbLockStrategy.setItems(FXCollections.observableArrayList(
                    "Deterministic Safe Strategy (Zero Deadlocks)",
                    "Naive Strategy (Deadlock Prone)",
                    "Timed Backoff Strategy (Non-Blocking)"
            ));
            cmbLockStrategy.getSelectionModel().select(0);
        }
    }

    private void setupButtonHandlers() {
        if (btnExecuteTransfer != null) {
            btnExecuteTransfer.setOnAction(e -> handleExecuteTransfer());
        }
        if (btnInjectVelocity != null) {
            btnInjectVelocity.setOnAction(e -> handleInjectTraffic());
        }
        if (btnTriggerDeadlock != null) {
            btnTriggerDeadlock.setOnAction(e -> handleTriggerDeadlock());
        }
        if (btnResetSystem != null) {
            btnResetSystem.setOnAction(e -> handleResetSystem());
        }
        if (btnRefreshLedger != null) {
            btnRefreshLedger.setOnAction(e -> refreshLedgerView());
        }
    }

    private void handleExecuteTransfer() {
        String srcId = txtSourceId.getText() != null ? txtSourceId.getText().trim() : "";
        String dstId = txtDestId.getText() != null ? txtDestId.getText().trim() : "";
        String amtStr = txtAmount.getText() != null ? txtAmount.getText().trim() : "";

        if (srcId.isEmpty() || dstId.isEmpty() || amtStr.isEmpty()) {
            showAlert("Input Error", "Please specify Source ID, Destination ID, and Amount.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amtStr);
        } catch (NumberFormatException e) {
            showAlert("Input Error", "Invalid transfer amount format: " + amtStr);
            return;
        }

        Account src = accountMap.get(srcId);
        Account dst = accountMap.get(dstId);

        if (src == null || dst == null) {
            showAlert("Account Not Found", "One or both account IDs do not exist in the ledger.");
            return;
        }

        String selectedStrategy = cmbLockStrategy != null ? cmbLockStrategy.getSelectionModel().getSelectedItem() : null;
        LockStrategy strategy;
        if (selectedStrategy != null && selectedStrategy.contains("Deadlock Prone")) {
            strategy = new LockStrategy.DeadlockProneLockStrategy();
        } else if (selectedStrategy != null && selectedStrategy.contains("Timed")) {
            strategy = new LockStrategy.TimedLockStrategy();
        } else {
            strategy = new LockStrategy.SafeLockStrategy();
        }

        workerThreadPool.submit(() -> {
            Transaction tx = transferEngine.processTransfer(src, dst, amount);
            FraudResult result = fraudEngine.evaluateTransaction(tx, src);

            JavaFXEventBridge.publishTransactionEvent(this, tx, result);
            JavaFXEventBridge.publishAccountEvent(this, src);
            JavaFXEventBridge.publishAccountEvent(this, dst);
        });
    }

    private void handleInjectTraffic() {
        Account src = accountMap.get("ACC-001");
        Account dst = accountMap.get("ACC-002");

        workerThreadPool.submit(() -> {
            for (int i = 0; i < 7; i++) {
                Transaction tx = transferEngine.processTransfer(src, dst, 1000.0);
                FraudResult result = fraudEngine.evaluateTransaction(tx, src);
                JavaFXEventBridge.publishTransactionEvent(this, tx, result);
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
            JavaFXEventBridge.publishAccountEvent(this, src);
            JavaFXEventBridge.publishAccountEvent(this, dst);
        });
    }

    private void handleTriggerDeadlock() {
        Account accA = accountMap.get("ACC-001");
        Account accB = accountMap.get("ACC-002");
        LockStrategy naiveStrategy = new LockStrategy.DeadlockProneLockStrategy(100);

        if (lblEngineStatus != null) {
            lblEngineStatus.setText("DEADLOCK DEMO");
            lblEngineStatus.setStyle("-fx-text-fill: #dc2626;");
        }

        workerThreadPool.submit(() -> {
            naiveStrategy.executeWithLocks(accA, accB, () -> {
                accA.debit(10.0);
                accB.credit(10.0);
            });
        });

        workerThreadPool.submit(() -> {
            naiveStrategy.executeWithLocks(accB, accA, () -> {
                accB.debit(10.0);
                accA.credit(10.0);
            });
        });

        workerThreadPool.submit(() -> {
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
            if (deadlockedThreads != null && deadlockedThreads.length > 0) {
                JavaFXEventBridge.publishDeadlockEvent(this,
                        String.format("DEADLOCK DETECTED: %d threads stuck in Coffman Circular Wait!", deadlockedThreads.length));
            }
        });
    }

    private void handleResetSystem() {
        seedInitialAccounts();
        observableTransactions.clear();
        fraudEngine.resetWindowHistory();
        if (lblEngineStatus != null) {
            lblEngineStatus.setText("RUNNING");
            lblEngineStatus.setStyle("-fx-text-fill: #4ade80;");
        }
        updateHeaderMetrics();
    }

    private void refreshLedgerView() {
        if (tblAccounts != null) tblAccounts.refresh();
        if (tblTransactions != null) tblTransactions.refresh();
        updateHeaderMetrics();
    }

    private void updateHeaderMetrics() {
        double totalBalance = 0.0;
        for (Account acc : accountMap.values()) {
            totalBalance += acc.getBalance();
        }
        if (lblTotalLiquidity != null) {
            lblTotalLiquidity.setText(String.format("RS %.2f", totalBalance));
        }
        if (lblQueueDepth != null) {
            lblQueueDepth.setText(pipelineQueue.size() + " / " + pipelineQueue.getCapacity());
        }
        if (lblActiveThreads != null) {
            lblActiveThreads.setText("5 ACTIVE");
        }
    }

    @Override
    public void onTransactionProcessed(Transaction transaction, FraudResult fraudResult) {
        if (transaction != null) {
            observableTransactions.add(0, transaction);
            refreshLedgerView();
        }
    }

    @Override
    public void onAccountBalanceChanged(Account account) {
        refreshLedgerView();
    }

    @Override
    public void onDeadlockDetected(String details) {
        showAlert("Deadlock Warning", details);
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.show();
        });
    }

    public void shutdown() {
        workerThreadPool.shutdown();
        pipelineQueue.shutdown();
    }
}
