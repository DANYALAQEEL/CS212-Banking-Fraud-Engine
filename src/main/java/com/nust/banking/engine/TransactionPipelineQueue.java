package com.nust.banking.engine;

import com.nust.banking.model.Transaction;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe, bounded, priority-aware transaction queue for streaming transaction processing.
 *
 * <p>Key Architecture Highlights:
 * <ul>
 *   <li><b>Priority-Aware:</b> Higher transfer amounts are ordered first (VIP processing).</li>
 *   <li><b>Bounded Capacity:</b> Uses fair {@link ReentrantLock} with dual {@link Condition} variables
 *       ({@code notFull}, {@code notEmpty}) to enforce backpressure.</li>
 *   <li><b>Telemetry & Metrics:</b> Atomic counters tracking total enqueued, dequeued, and queue depth.</li>
 *   <li><b>Graceful Shutdown:</b> Supports clean shutdown signaling waiting threads to terminate.</li>
 * </ul>
 *
 * @author Danyal Aqeel
 */
public class TransactionPipelineQueue {

    private final int capacity;
    private final PriorityQueue<Transaction> queue;
    private final ReentrantLock lock;
    private final Condition notFull;
    private final Condition notEmpty;

    private final AtomicLong totalEnqueued = new AtomicLong(0);
    private final AtomicLong totalDequeued = new AtomicLong(0);
    private volatile boolean shutdown = false;

    /**
     * Comparator prioritizing larger transaction amounts first (descending).
     * Secondary tie-breaker orders earlier timestamp first (FIFO within same amount).
     */
    private static final Comparator<Transaction> PRIORITY_COMPARATOR = (t1, t2) -> {
        int amountCompare = Double.compare(t2.amount(), t1.amount()); // Descending amount
        if (amountCompare != 0) {
            return amountCompare;
        }
        return t1.timestamp().compareTo(t2.timestamp()); // Ascending timestamp
    };

    /**
     * Constructs a TransactionPipelineQueue with the specified maximum capacity.
     *
     * @param capacity maximum number of buffered transactions (must be positive)
     */
    public TransactionPipelineQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive; received: " + capacity);
        }
        this.capacity = capacity;
        this.queue = new PriorityQueue<>(capacity, PRIORITY_COMPARATOR);
        this.lock = new ReentrantLock(true); // Fair lock policy
        this.notFull = lock.newCondition();
        this.notEmpty = lock.newCondition();
    }

    /**
     * Inserts a transaction into the queue, blocking if necessary until space becomes available.
     *
     * @param tx transaction to enqueue
     * @throws InterruptedException if interrupted while waiting
     * @throws IllegalStateException if queue has been shut down
     */
    public void put(Transaction tx) throws InterruptedException {
        if (tx == null) {
            throw new NullPointerException("Cannot enqueue null transaction");
        }
        lock.lockInterruptibly();
        try {
            while (queue.size() == capacity && !shutdown) {
                notFull.await();
            }
            if (shutdown) {
                throw new IllegalStateException("Queue is shut down; rejecting new transactions");
            }
            queue.add(tx);
            totalEnqueued.incrementAndGet();
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts a transaction into the queue, waiting up to the specified timeout if necessary.
     *
     * @param tx      transaction to enqueue
     * @param timeout maximum time to wait
     * @param unit    time unit of the timeout
     * @return {@code true} if successful, {@code false} if timeout elapsed or shut down
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean offer(Transaction tx, long timeout, TimeUnit unit) throws InterruptedException {
        if (tx == null) {
            throw new NullPointerException("Cannot enqueue null transaction");
        }
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (queue.size() == capacity) {
                if (shutdown || nanos <= 0L) {
                    return false;
                }
                nanos = notFull.awaitNanos(nanos);
            }
            if (shutdown) {
                return false;
            }
            queue.add(tx);
            totalEnqueued.incrementAndGet();
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the highest-priority transaction from the queue, blocking if necessary
     * until an element becomes available or the queue is shut down.
     *
     * @return the highest priority transaction, or {@code null} if shut down and empty
     * @throws InterruptedException if interrupted while waiting
     */
    public Transaction take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (queue.isEmpty() && !shutdown) {
                notEmpty.await();
            }
            if (queue.isEmpty() && shutdown) {
                return null;
            }
            Transaction tx = queue.poll();
            if (tx != null) {
                totalDequeued.incrementAndGet();
                notFull.signal();
            }
            return tx;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the highest-priority transaction, waiting up to specified timeout.
     *
     * @param timeout maximum time to wait
     * @param unit    time unit of timeout
     * @return transaction, or {@code null} if timeout elapsed or queue shut down
     * @throws InterruptedException if interrupted while waiting
     */
    public Transaction poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (queue.isEmpty()) {
                if (shutdown || nanos <= 0L) {
                    return null;
                }
                nanos = notEmpty.awaitNanos(nanos);
            }
            Transaction tx = queue.poll();
            if (tx != null) {
                totalDequeued.incrementAndGet();
                notFull.signal();
            }
            return tx;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Initiates graceful shutdown, unblocking any producers or consumers waiting on condition variables.
     */
    public void shutdown() {
        lock.lock();
        try {
            shutdown = true;
            notFull.signalAll();
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    public int getCapacity() {
        return capacity;
    }

    public long getTotalEnqueued() {
        return totalEnqueued.get();
    }

    public long getTotalDequeued() {
        return totalDequeued.get();
    }

    public boolean isShutdown() {
        return shutdown;
    }
}
