package com.nust.banking.persistence;

import com.nust.banking.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 5: File Persistence & Binary State Serialization Test Suite")
class PersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("CSV Persistence: Export and import account ledgers preserving subclass polymorphic parameters")
    void testCsvExportAndImport() throws Exception {
        File csvFile = tempDir.resolve("account_ledger.csv").toFile();

        SavingsAccount sav = new SavingsAccount("ACC-001", "Alice Smith", 500000.0, 0.05, 1000.0);
        CheckingAccount chk = new CheckingAccount("ACC-002", "Bob Jones", 250000.0, 5000.0, 25.0);
        CreditAccount cred = new CreditAccount("ACC-003", "Charlie Brown", 15000.0, 50000.0, 0.18);

        List<Account> original = List.of(sav, chk, cred);

        LedgerFileManager.exportAccountsToCsv(original, csvFile);
        assertTrue(csvFile.exists() && csvFile.length() > 0);

        List<Account> imported = LedgerFileManager.importAccountsFromCsv(csvFile);
        assertEquals(3, imported.size());

        Account imp1 = imported.get(0);
        assertTrue(imp1 instanceof SavingsAccount);
        assertEquals("ACC-001", imp1.getId());
        assertEquals("Alice Smith", imp1.getOwnerName());
        assertEquals(500000.0, imp1.getBalance());

        Account imp2 = imported.get(1);
        assertTrue(imp2 instanceof CheckingAccount);
        assertEquals("Bob Jones", imp2.getOwnerName());

        Account imp3 = imported.get(2);
        assertTrue(imp3 instanceof CreditAccount);
        assertEquals("Charlie Brown", imp3.getOwnerName());
    }

    @Test
    @DisplayName("JSON Audit Stream: Export transactions to formatted JSON file")
    void testJsonTransactionExport() throws Exception {
        File jsonFile = tempDir.resolve("transactions.json").toFile();

        Transaction tx1 = Transaction.createNew("ACC-001", "ACC-002", 1500.0);
        Transaction tx2 = Transaction.createNew("ACC-002", "ACC-003", 250.0);

        LedgerFileManager.exportTransactionsToJson(List.of(tx1, tx2), jsonFile);

        assertTrue(jsonFile.exists() && jsonFile.length() > 0);
        String jsonContent = Files.readString(jsonFile.toPath());

        assertTrue(jsonContent.contains("ACC-001"));
        assertTrue(jsonContent.contains("ACC-002"));
        assertTrue(jsonContent.contains("1500.00"));
    }

    @Test
    @DisplayName("Binary Snapshot Serialization: Save and restore system state snapshot via ObjectOutputStream")
    void testBinaryStateSerialization() throws Exception {
        File snapFile = tempDir.resolve("engine_snapshot.bin").toFile();

        SavingsAccount sav = new SavingsAccount("ACC-001", "Alice Smith", 500000.0, 0.05, 1000.0);
        CheckingAccount chk = new CheckingAccount("ACC-002", "Bob Jones", 250000.0, 5000.0, 25.0);

        Transaction tx = Transaction.createNew("ACC-001", "ACC-002", 5000.0);
        Map<String, String> meta = Map.of("Version", "1.0.0", "Status", "HEALTHY");

        BinaryStateSerializer.EngineSnapshot snapshot = new BinaryStateSerializer.EngineSnapshot(
                System.currentTimeMillis(),
                List.of(sav, chk),
                List.of(tx),
                meta
        );

        BinaryStateSerializer.saveSnapshot(snapshot, snapFile);
        assertTrue(snapFile.exists() && snapFile.length() > 0);

        BinaryStateSerializer.EngineSnapshot restored = BinaryStateSerializer.loadSnapshot(snapFile);

        assertNotNull(restored);
        assertEquals(2, restored.accountSnapshots().size());
        assertEquals(1, restored.transactionHistory().size());
        assertEquals("HEALTHY", restored.systemMetadata().get("Status"));

        Account restoredAcc = restored.accountSnapshots().get(0);
        assertEquals("ACC-001", restoredAcc.getId());
        assertEquals("Alice Smith", restoredAcc.getOwnerName());
        assertNotNull(restoredAcc.getLock(), "Transient ReentrantLock should be re-initialized upon deserialization");
    }
}
