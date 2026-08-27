package me.bokan.perocasino.data;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataTest {

    private static PlayerData data() {
        return new PlayerData(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    void depositThenWithdraw() {
        PlayerData d = data();
        assertTrue(d.tryDepositWallet(10));
        assertEquals(10, d.getWalletBalance());
        assertTrue(d.tryWithdrawWallet(4));
        assertEquals(6, d.getWalletBalance());
        assertFalse(d.tryWithdrawWallet(7));
        assertEquals(6, d.getWalletBalance());
    }

    @Test
    void depositOverflowIsRejected() {
        PlayerData d = data();
        d.setWalletBalance(Integer.MAX_VALUE - 1);
        assertFalse(d.tryDepositWallet(2));
        assertEquals(Integer.MAX_VALUE - 1, d.getWalletBalance());
        assertTrue(d.tryDepositWallet(1));
        assertEquals(Integer.MAX_VALUE, d.getWalletBalance());
    }

    @Test
    void addWalletBalanceNegativeDoesNotClampWipe() {
        PlayerData d = data();
        d.setWalletBalance(5);
        assertFalse(d.addWalletBalance(-20));
        assertEquals(5, d.getWalletBalance());
    }

    @Test
    void borrowIsAtomic() {
        PlayerData d = data();
        d.setWalletBalance(Integer.MAX_VALUE - 5);
        d.setDebt(10);
        assertFalse(d.tryBorrow(10));
        assertEquals(Integer.MAX_VALUE - 5, d.getWalletBalance());
        assertEquals(10, d.getDebt());
        assertTrue(d.tryBorrow(5));
        assertEquals(Integer.MAX_VALUE, d.getWalletBalance());
        assertEquals(15, d.getDebt());
    }

    @Test
    void repayUsesLiveMinAndClearsTimer() {
        PlayerData d = data();
        d.setWalletBalance(40);
        d.setDebt(25);
        d.setLoanDeadlineMillis(123);
        d.setNextInterestMillis(456);
        assertEquals(25, d.tryRepay(100));
        assertEquals(15, d.getWalletBalance());
        assertEquals(0, d.getDebt());
        assertEquals(0L, d.getLoanDeadlineMillis());
        assertEquals(0L, d.getNextInterestMillis());
    }

    @Test
    void interestCapsInsteadOfWrappingToZero() {
        PlayerData d = data();
        d.setDebt(Integer.MAX_VALUE - 3);
        assertEquals(Integer.MAX_VALUE, d.applyInterest(10));
        assertEquals(Integer.MAX_VALUE, d.getDebt());
    }

    @Test
    void repayZeroWhenNoDebt() {
        PlayerData d = data();
        d.setWalletBalance(50);
        assertEquals(0, d.tryRepay(10));
        assertEquals(50, d.getWalletBalance());
    }
}
