package com.nust.banking.persistence;

import com.nust.banking.model.*;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * File I/O manager providing CSV and JSON serialization for account ledgers
 * and transaction audit streams.
 *
 * @author Hamza Zahoor
 */
public class LedgerFileManager {

    private static final String CSV_HEADER = "ID,Type,OwnerName,Balance,Param1,Param2";

    /**
     * Exports a collection of account objects to a structured CSV file.
     */
    public static void exportAccountsToCsv(Collection<Account> accounts, File targetFile) throws IOException {
        if (targetFile == null) {
            throw new IllegalArgumentException("Target file cannot be null");
        }

        try (BufferedWriter writer = Files.newBufferedWriter(targetFile.toPath())) {
            writer.write(CSV_HEADER);
            writer.newLine();

            for (Account acc : accounts) {
                String type = acc.getClass().getSimpleName();
                String line;
                if (acc instanceof SavingsAccount sav) {
                    line = String.format("%s,%s,%s,%.2f,%.4f,%.2f",
                            sav.getId(), type, sav.getOwnerName(), sav.getBalance(),
                            sav.getInterestRate(), sav.getMinimumBalance());
                } else if (acc instanceof CheckingAccount chk) {
                    line = String.format("%s,%s,%s,%.2f,%.2f,%.2f",
                            chk.getId(), type, chk.getOwnerName(), chk.getBalance(),
                            chk.getOverdraftLimit(), chk.getTransactionFee());
                } else if (acc instanceof CreditAccount cred) {
                    line = String.format("%s,%s,%s,%.2f,%.2f,%.4f",
                            cred.getId(), type, cred.getOwnerName(), cred.getBalance(),
                            cred.getCreditLimit(), cred.getApr());
                } else {
                    line = String.format("%s,%s,%s,%.2f,0.0,0.0",
                            acc.getId(), type, acc.getOwnerName(), acc.getBalance());
                }
                writer.write(line);
                writer.newLine();
            }
        }
    }

    /**
     * Imports account objects from a structured CSV file.
     */
    public static List<Account> importAccountsFromCsv(File sourceFile) throws IOException {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new FileNotFoundException("CSV source file does not exist");
        }

        List<Account> importedAccounts = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(sourceFile.toPath())) {
            String line = reader.readLine(); // Skip header
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length < 6) continue;

                String id = parts[0].trim();
                String type = parts[1].trim();
                String owner = parts[2].trim();
                double balance = Double.parseDouble(parts[3].trim());
                double p1 = Double.parseDouble(parts[4].trim());
                double p2 = Double.parseDouble(parts[5].trim());

                Account acc;
                if ("SavingsAccount".equalsIgnoreCase(type)) {
                    acc = new SavingsAccount(id, owner, balance, p1, p2);
                } else if ("CheckingAccount".equalsIgnoreCase(type)) {
                    acc = new CheckingAccount(id, owner, balance, p1, p2);
                } else if ("CreditAccount".equalsIgnoreCase(type)) {
                    acc = new CreditAccount(id, owner, balance, p1, p2);
                } else {
                    continue;
                }
                importedAccounts.add(acc);
            }
        }
        return importedAccounts;
    }

    /**
     * Exports transaction audit logs to a JSON file.
     */
    public static void exportTransactionsToJson(List<Transaction> transactions, File targetFile) throws IOException {
        if (targetFile == null) {
            throw new IllegalArgumentException("Target file cannot be null");
        }

        try (BufferedWriter writer = Files.newBufferedWriter(targetFile.toPath())) {
            writer.write("[\n");
            for (int i = 0; i < transactions.size(); i++) {
                Transaction tx = transactions.get(i);
                String jsonStr = String.format(
                        "  {\n" +
                        "    \"transactionId\": \"%s\",\n" +
                        "    \"fromAccountId\": \"%s\",\n" +
                        "    \"toAccountId\": \"%s\",\n" +
                        "    \"amount\": %.2f,\n" +
                        "    \"timestamp\": \"%s\",\n" +
                        "    \"status\": \"%s\",\n" +
                        "    \"statusDetails\": \"%s\"\n" +
                        "  }%s",
                        tx.transactionId(), tx.fromAccountId(), tx.toAccountId(),
                        tx.amount(), tx.timestamp().toString(), tx.status().name(),
                        tx.statusDetails().replace("\"", "\\\""),
                        (i < transactions.size() - 1) ? ",\n" : "\n"
                );
                writer.write(jsonStr);
            }
            writer.write("]\n");
        }
    }
}
