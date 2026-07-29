package com.nust.banking.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Account Subclasses & Lock Ordering Unit Tests")
public class AccountTest {

    private SavingsAccount savings;
    private CheckingAccount checking;
    private CreditAccount credit;

    @BeforeEach
    void setUp() {
        savings = new SavingsAccount("ACC-101", "Danyal Aqeel", 5000.0, 0.05, 1000.0);
        checking = new CheckingAccount("ACC-102", "Muhammad Arslan", 2000.0, 1000.0, 25.0);
        credit = new CreditAccount("ACC-103", "Hamza Zahoor", 0.0, 50000.0, 0.18);
    }

    @Test
    @DisplayName("SavingsAccount: Valid withdrawal reduces balance correctly")
    void testSavingsValidDebit() {
        savings.debit(2000.0);
        assertEquals(3000.0, savings.getBalance(), 0.001);
    }

    @Test
    @DisplayName("SavingsAccount: Withdrawal violating minimum balance throws IllegalStateException")
    void testSavingsMinimumBalanceViolation() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> savings.debit(4500.0));
        assertTrue(ex.getMessage().contains("minimum balance requirement"));
    }

    @Test
    @DisplayName("SavingsAccount: Interest application increases balance correctly")
    void testSavingsApplyInterest() {
        savings.applyInterest(); // 5000 * 0.05 = 250 -> 5250
        assertEquals(5250.0, savings.getBalance(), 0.001);
    }

    @Test
    @DisplayName("CheckingAccount: Debit deducts transaction fee automatically")
    void testCheckingDebitWithFee() {
        checking.debit(500.0); // 500 + 25 fee = 525 -> 1475 balance
        assertEquals(1475.0, checking.getBalance(), 0.001);
    }

    @Test
    @DisplayName("CheckingAccount: Overdraft limit allows balance to drop below zero up to limit")
    void testCheckingOverdraftAllowed() {
        checking.debit(2500.0); // 2500 + 25 fee = 2525 -> -525 balance (Limit is -1000)
        assertEquals(-525.0, checking.getBalance(), 0.001);
    }

    @Test
    @DisplayName("CheckingAccount: Debit exceeding overdraft limit throws IllegalStateException")
    void testCheckingExceedingOverdraft() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> checking.debit(3500.0));
        assertTrue(ex.getMessage().contains("exceeds overdraft limit"));
    }

    @Test
    @DisplayName("CreditAccount: Spending increases debt balance up to credit limit")
    void testCreditDebitIncreasesDebt() {
        credit.debit(10000.0);
        assertEquals(10000.0, credit.getBalance(), 0.001);
        assertEquals(40000.0, credit.getAvailableCredit(), 0.001);
    }

    @Test
    @DisplayName("CreditAccount: Spending exceeding credit limit throws IllegalStateException")
    void testCreditExceedingLimit() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> credit.debit(60000.0));
        assertTrue(ex.getMessage().contains("exceeds credit limit"));
    }

    @Test
    @DisplayName("Account Deterministic Lock Ordering: compareTo sorts lexicographically by Account ID")
    void testAccountCompareToLockOrdering() {
        assertTrue(savings.compareTo(checking) < 0, "ACC-101 should precede ACC-102");
        assertTrue(checking.compareTo(credit) < 0, "ACC-102 should precede ACC-103");
        assertEquals(0, savings.compareTo(savings), "Same account ID should return 0");
    }
}
