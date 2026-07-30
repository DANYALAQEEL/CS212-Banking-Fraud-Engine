package com.nust.banking.engine;

import com.nust.banking.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TransactionPipelineQueue Concurrency & Priority Test Suite")
class TransactionPipelineQueueTest {

    private TransactionPipelineQueue pipelineQueue;

    @BeforeEach
    void setUp() {
        pipelineQueue = new TransactionPipelineQueue(5);
    }

    @Test
    @DisplayName("Priority Ordering: Higher transfer amounts must be dequeued first")
    void testPriorityOrderingHighAmountFirst() throws InterruptedException {
        Transaction txLow  = Transaction.createNew("ACC-001", "ACC-002", 500.0);
        Transaction txHigh = Transaction.createNew("ACC-001", "ACC-003", 50000.0);
        Transaction txMed  = Transaction.createNew("ACC-002", "ACC-003", 5000.0);

        pipelineQueue.put(txLow);
        pipelineQueue.put(txHigh);
        pipelineQueue.put(txMed);

        assertEquals(3, pipelineQueue.size());

        Transaction firstOut = pipelineQueue.take();
        assertEquals(50000.0, firstOut.amount());

        Transaction secondOut = pipelineQueue.take();
        assertEquals(5000.0, secondOut.amount());

        Transaction thirdOut = pipelineQueue.take();
        assertEquals(500.0, thirdOut.amount());
    }

    @Test
    @DisplayName("Bounded Capacity: Producer blocks when queue reaches max capacity")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testBoundedCapacityBlocking() throws InterruptedException, ExecutionException, TimeoutException {
        for (int i = 1; i <= 5; i++) {
            pipelineQueue.put(Transaction.createNew("ACC-A", "ACC-B", i * 100.0));
        }

        assertEquals(5, pipelineQueue.size());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> {
            try {
                pipelineQueue.put(Transaction.createNew("ACC-A", "ACC-B", 999.0));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(200);
        assertFalse(future.isDone(), "Producer thread should be blocked waiting on full queue");

        Transaction dequeued = pipelineQueue.take();
        assertNotNull(dequeued);

        future.get(2, TimeUnit.SECONDS);
        assertEquals(5, pipelineQueue.size());
        assertEquals(6, pipelineQueue.getTotalEnqueued());

        executor.shutdown();
    }

    @Test
    @DisplayName("Concurrent Handoff: Multi-producer multi-consumer high-throughput test")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentMultiProducerMultiConsumer() throws InterruptedException {
        int capacity = 100;
        TransactionPipelineQueue queue = new TransactionPipelineQueue(capacity);

        int producerCount = 5;
        int itemsPerProducer = 100;
        int totalItems = producerCount * itemsPerProducer;

        ExecutorService producers = Executors.newFixedThreadPool(producerCount);
        ExecutorService consumers = Executors.newFixedThreadPool(3);

        AtomicInteger totalReceived = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(totalItems);

        for (int c = 0; c < 3; c++) {
            consumers.submit(() -> {
                try {
                    while (totalReceived.get() < totalItems) {
                        Transaction tx = queue.poll(500, TimeUnit.MILLISECONDS);
                        if (tx != null) {
                            totalReceived.incrementAndGet();
                            latch.countDown();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        for (int p = 0; p < producerCount; p++) {
            final int producerId = p;
            producers.submit(() -> {
                for (int i = 0; i < itemsPerProducer; i++) {
                    try {
                        Transaction tx = Transaction.createNew("ACC-P" + producerId, "ACC-DIST", (i + 1) * 10.0);
                        queue.put(tx);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        boolean completed = latch.await(8, TimeUnit.SECONDS);
        assertTrue(completed, "All transactions should be produced and consumed cleanly");
        assertEquals(totalItems, totalReceived.get());
        assertEquals(totalItems, queue.getTotalEnqueued());
        assertEquals(totalItems, queue.getTotalDequeued());

        producers.shutdown();
        consumers.shutdown();
    }

    @Test
    @DisplayName("Graceful Shutdown: Unblocks waiting consumers when shut down")
    void testGracefulShutdown() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<Transaction> takeFuture = executor.submit(() -> pipelineQueue.take());

        Thread.sleep(100);
        assertFalse(takeFuture.isDone(), "Consumer should block on empty queue");

        pipelineQueue.shutdown();

        Transaction result = takeFuture.get();
        assertNull(result, "Consumer should return null when queue is shut down");
        assertTrue(pipelineQueue.isShutdown());

        executor.shutdown();
    }
}
