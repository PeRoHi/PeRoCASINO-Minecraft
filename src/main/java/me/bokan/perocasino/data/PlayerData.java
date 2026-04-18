package me.bokan.perocasino.data;

import java.util.UUID;

/**
 * プレイヤーごとの経済データを保持するクラス。
 * walletBalance (財布残高) と debt (借金額) はどちらも 0 以上。
 */
public class PlayerData {

    private final UUID playerId;
    private int walletBalance = 0;
    private int debt = 0;
    /** 返済期限（Unix ミリ秒）。0 = 借金なし。 */
    private long loanDeadlineMillis = 0L;
    /** 次回利息適用時刻（Unix ミリ秒）。0 = 利息タイマー未起動。 */
    private long nextInterestMillis = 0L;

    public PlayerData(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() { return playerId; }

    // --- 財布残高 ---
    public int getWalletBalance() { return walletBalance; }
    public void setWalletBalance(int amount) { walletBalance = Math.max(0, amount); }
    public void addWalletBalance(int amount) { setWalletBalance(walletBalance + amount); }

    // --- 借金 ---
    public int getDebt() { return debt; }
    public void setDebt(int amount) { debt = Math.max(0, amount); }
    public void addDebt(int amount) { setDebt(debt + amount); }

    // --- ローンタイマー ---
    public long getLoanDeadlineMillis() { return loanDeadlineMillis; }
    public void setLoanDeadlineMillis(long millis) { loanDeadlineMillis = millis; }

    public long getNextInterestMillis() { return nextInterestMillis; }
    public void setNextInterestMillis(long millis) { nextInterestMillis = millis; }
}
