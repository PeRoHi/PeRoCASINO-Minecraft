package me.bokan.perocasino.data;

import java.util.UUID;

/**
 * プレイヤーごとの経済データを保持するクラス。
 * walletBalance (財布残高) と debt (借金額) はどちらも 0 以上。
 * 加算は int オーバーフローで負数／0 に潰さない。
 */
public class PlayerData {

    private final UUID playerId;
    private int walletBalance = 0;
    private int debt = 0;
    private int lastRouletteIndex = 0;
    /** 返済期限（Unix ミリ秒）。0 = 借金なし。 */
    private long loanDeadlineMillis = 0L;
    /** 次回利息適用時刻（Unix ミリ秒）。0 = 利息タイマー未起動。 */
    private long nextInterestMillis = 0L;

    public PlayerData(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() { return playerId; }

    public int getLastRouletteIndex() { return lastRouletteIndex; }
    public void setLastRouletteIndex(int index) { this.lastRouletteIndex = index; }

    // --- 財布残高 ---
    public synchronized int getWalletBalance() { return walletBalance; }

    public synchronized void setWalletBalance(int amount) {
        walletBalance = Math.max(0, amount);
    }

    /**
     * 財布へ入金する。amount &lt; 0、または int を超える加算は拒否して残高を変えない。
     */
    public synchronized boolean tryDepositWallet(int amount) {
        if (amount < 0) {
            return false;
        }
        if (amount == 0) {
            return true;
        }
        long sum = (long) walletBalance + (long) amount;
        if (sum > Integer.MAX_VALUE) {
            return false;
        }
        walletBalance = (int) sum;
        return true;
    }

    /**
     * 財布から出金する。不足・負の amount は拒否して残高を変えない。
     */
    public synchronized boolean tryWithdrawWallet(int amount) {
        if (amount < 0) {
            return false;
        }
        if (amount == 0) {
            return true;
        }
        if (walletBalance < amount) {
            return false;
        }
        walletBalance -= amount;
        return true;
    }

    /**
     * 正なら入金、負なら出金。拒否時は false（残高据え置き）。
     */
    public synchronized boolean addWalletBalance(int delta) {
        if (delta >= 0) {
            return tryDepositWallet(delta);
        }
        if (delta == Integer.MIN_VALUE) {
            return false;
        }
        return tryWithdrawWallet(-delta);
    }

    // --- 借金 ---
    public synchronized int getDebt() { return debt; }

    public synchronized void setDebt(int amount) {
        debt = Math.max(0, amount);
    }

    /**
     * 借金の増減。増加が int を超える場合は MAX_VALUE にキャップ（0 へラップしない）。
     * 減少は 0 未満にしない。不足して減らせない場合は false。
     */
    public synchronized boolean addDebt(int delta) {
        if (delta == 0) {
            return true;
        }
        if (delta > 0) {
            long sum = (long) debt + (long) delta;
            debt = (int) Math.min(Integer.MAX_VALUE, sum);
            return true;
        }
        if (delta == Integer.MIN_VALUE) {
            return false;
        }
        int reduce = -delta;
        if (debt < reduce) {
            return false;
        }
        debt -= reduce;
        return true;
    }

    /**
     * 借入：財布と借金を同じ額だけ増やす。どちらかが溢れるならどちらも変えない。
     */
    public synchronized boolean tryBorrow(int amount) {
        if (amount <= 0) {
            return false;
        }
        long newWallet = (long) walletBalance + (long) amount;
        long newDebt = (long) debt + (long) amount;
        if (newWallet > Integer.MAX_VALUE || newDebt > Integer.MAX_VALUE) {
            return false;
        }
        walletBalance = (int) newWallet;
        debt = (int) newDebt;
        return true;
    }

    /**
     * 返済：財布と借金から同じ額を減らす。
     * @return 実際に返済した額（0 なら何も変えていない）
     */
    public synchronized int tryRepay(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int actual = Math.min(amount, Math.min(walletBalance, debt));
        if (actual <= 0) {
            return 0;
        }
        walletBalance -= actual;
        debt -= actual;
        if (debt == 0) {
            loanDeadlineMillis = 0L;
            nextInterestMillis = 0L;
        }
        return actual;
    }

    /**
     * 利息を乗せる。int 溢れは MAX_VALUE キャップ。
     * @return 適用後の借金
     */
    public synchronized int applyInterest(int interest) {
        if (interest > 0) {
            addDebt(interest);
        }
        return debt;
    }

    // --- ローンタイマー ---
    public synchronized long getLoanDeadlineMillis() { return loanDeadlineMillis; }
    public synchronized void setLoanDeadlineMillis(long millis) { loanDeadlineMillis = millis; }

    public synchronized long getNextInterestMillis() { return nextInterestMillis; }
    public synchronized void setNextInterestMillis(long millis) { nextInterestMillis = millis; }
}
