package com.nust.banking.persistence;

import com.nust.banking.model.Account;
import com.nust.banking.model.Transaction;

import java.io.*;
import java.util.*;

/**
 * Java Binary Serialization Manager for high-speed engine state snapshotting
 * and state restoration.
 *
 * @author Hamza Zahoor
 */
public class BinaryStateSerializer {

    /**
     * Serializable snapshot model encapsulating total system state.
     */
    public record EngineSnapshot(
            long snapshotTimestamp,
            List<Account> accountSnapshots,
            List<Transaction> transactionHistory,
            Map<String, String> systemMetadata
    ) implements Serializable {
        private static final long serialVersionUID = 1L;

        public EngineSnapshot(Collection<Account> accounts, List<Transaction> transactions) {
            this(System.currentTimeMillis(), new ArrayList<>(accounts), new ArrayList<>(transactions), Map.of());
        }

        public List<Account> getAccounts() {
            return accountSnapshots;
        }

        public List<Transaction> getTransactions() {
            return transactionHistory;
        }
    }

    /**
     * Saves the current engine state to a compressed binary snapshot file.
     */
    public static void saveSnapshot(EngineSnapshot snapshot, File targetFile) throws IOException {
        if (snapshot == null || targetFile == null) {
            throw new IllegalArgumentException("Snapshot and target file must not be null");
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(targetFile))) {
            oos.writeObject(snapshot);
            oos.flush();
        }
    }

    /**
     * Restores an engine state snapshot from a binary snapshot file.
     *
     * <p>Deserialization is guarded by an {@link ObjectInputFilter} allowlist so that a tampered
     * snapshot cannot instantiate arbitrary gadget classes. {@code java.time.Instant} and its serialization
     * proxy {@code java.time.Ser} are allowed because {@link Transaction#timestamp()} is an {@code Instant}.
     */
    public static EngineSnapshot loadSnapshot(File sourceFile) throws IOException, ClassNotFoundException {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new FileNotFoundException("Binary snapshot file does not exist");
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(sourceFile))) {
            ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
                    "com.nust.banking.persistence.BinaryStateSerializer$EngineSnapshot;" +
                    "com.nust.banking.model.*;" +
                    "java.util.*;" +
                    "java.lang.*;" +
                    "java.time.Instant;" +
                    "java.time.Ser;" +
                    "!*"
            );
            ois.setObjectInputFilter(filter);
            return (EngineSnapshot) ois.readObject();
        }
    }
}
