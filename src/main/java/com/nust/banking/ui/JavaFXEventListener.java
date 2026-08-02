package com.nust.banking.ui;

import com.nust.banking.model.Account;
import com.nust.banking.model.FraudResult;
import com.nust.banking.model.Transaction;
import javafx.application.Platform;

/**
 * Interface and thread-safe event bridge for routing background engine telemetry
 * onto the JavaFX Application Thread without freezing the GUI.
 *
 * @author Muhammad Arslan
 */
public interface JavaFXEventListener {

    /**
     * Triggered when a transaction completes settlement or fraud evaluation.
     *
     * @param transaction processed transaction record
     * @param fraudResult fraud evaluation result token (may be null if cleared)
     */
    void onTransactionProcessed(Transaction transaction, FraudResult fraudResult);

    /**
     * Triggered when an account balance is updated inside a lock boundary.
     *
     * @param account account instance with updated balance
     */
    void onAccountBalanceChanged(Account account);

    /**
     * Triggered when a thread deadlock state is detected.
     *
     * @param details thread deadlock diagnostic message
     */
    void onDeadlockDetected(String details);

    /**
     * Static utility bridge guaranteeing Platform.runLater execution.
     */
    class JavaFXEventBridge {
        public static void publishTransactionEvent(JavaFXEventListener listener, Transaction tx, FraudResult result) {
            if (listener != null && tx != null) {
                Platform.runLater(() -> listener.onTransactionProcessed(tx, result));
            }
        }

        public static void publishAccountEvent(JavaFXEventListener listener, Account account) {
            if (listener != null && account != null) {
                Platform.runLater(() -> listener.onAccountBalanceChanged(account));
            }
        }

        public static void publishDeadlockEvent(JavaFXEventListener listener, String details) {
            if (listener != null && details != null) {
                Platform.runLater(() -> listener.onDeadlockDetected(details));
            }
        }
    }
}
