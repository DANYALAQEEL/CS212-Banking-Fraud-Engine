package com.nust.banking.engine;

import com.nust.banking.model.Account;
import com.nust.banking.model.Transaction;
import com.nust.banking.ui.JavaFXEventListener;
import com.nust.banking.ui.JavaFXEventListener.JavaFXEventBridge;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates the asynchronous streaming transaction processing pipeline.
 * Producer thread enqueues transfers into the TransactionPipelineQueue (bounded 100).
 * Consumer worker threads consume transactions from the queue, execute pre-execution fraud
 * evaluation and atomic settlement via SettlementService, and dispatch updates to listeners.
 */
public class PipelineCoordinator {
    private final TransactionPipelineQueue pipelineQueue;
    private final SettlementService settlementService;
    private final ExecutorService consumerPool;
    private final List<JavaFXEventListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Account> accountMap;

    public PipelineCoordinator(SettlementService settlementService, Map<String, Account> accountMap) {
        this.pipelineQueue = new TransactionPipelineQueue(100);
        this.settlementService = settlementService != null ? settlementService : new SettlementService(new TransferEngine(), new FraudDetectionEngine());
        this.accountMap = accountMap;
        this.consumerPool = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "pipeline-consumer-worker");
            t.setDaemon(true);
            return t;
        });

        startConsumerWorkers();
    }

    public void addListener(JavaFXEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(JavaFXEventListener listener) {
        listeners.remove(listener);
    }

    public boolean submitTransaction(Transaction tx, long timeout, TimeUnit unit) throws InterruptedException {
        return pipelineQueue.offer(tx, timeout, unit);
    }

    private void startConsumerWorkers() {
        for (int i = 0; i < 3; i++) {
            consumerPool.submit(() -> {
                while (!pipelineQueue.isShutdown() && !Thread.currentThread().isInterrupted()) {
                    try {
                        Transaction tx = pipelineQueue.take();
                        if (tx == null) break;

                        Account fromAcc = accountMap.get(tx.fromAccountId());
                        Account toAcc = accountMap.get(tx.toAccountId());
                        Account feeAcc = accountMap.get("BANK-FEES");

                        SettlementService.SettlementOutcome outcome = settlementService.settle(
                            fromAcc, toAcc, feeAcc, tx.amount(), new LockStrategy.SafeLockStrategy()
                        );

                        notifyListeners(outcome.transaction(), outcome.fraudResult());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }
    }

    private void notifyListeners(Transaction tx, com.nust.banking.model.FraudResult result) {
        for (JavaFXEventListener listener : listeners) {
            JavaFXEventBridge.publishTransactionEvent(listener, tx, result);
        }
    }

    public int getQueueSize() {
        return pipelineQueue.size();
    }

    public long getTotalEnqueued() {
        return pipelineQueue.getTotalEnqueued();
    }

    public long getTotalDequeued() {
        return pipelineQueue.getTotalDequeued();
    }

    public TransactionPipelineQueue getPipelineQueue() {
        return pipelineQueue;
    }

    public void shutdown() {
        pipelineQueue.shutdown();
        consumerPool.shutdown();
        try {
            if (!consumerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                consumerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            consumerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
