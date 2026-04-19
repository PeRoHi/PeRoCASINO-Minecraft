package me.bokan.perocasino.economy;

import me.bokan.perocasino.data.PlayerData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤーの経済データ（財布残高・借金）をメモリ上で管理するクラス。
 */
public class EconomyManager {

    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();

    /** プレイヤーデータを取得する。存在しない場合は新規作成して返す。 */
    public PlayerData getData(UUID playerId) {
        return playerDataMap.computeIfAbsent(playerId, PlayerData::new);
    }

    public boolean hasData(UUID playerId) {
        return playerDataMap.containsKey(playerId);
    }
    public int getLastRouletteIndex(UUID uuid) {
        return getPlayerData(uuid).getLastRouletteIndex();
    }
    public void setLastRouletteIndex(UUID uuid, int index) {
        getPlayerData(uuid).setLastRouletteIndex(index);
    }

    // --- 財布残高 ---
    public int getWalletBalance(UUID id) { return getData(id).getWalletBalance(); }
    public void setWalletBalance(UUID id, int amount) { getData(id).setWalletBalance(amount); }
    public void addWalletBalance(UUID id, int amount) { getData(id).addWalletBalance(amount); }

    // --- 借金 ---
    public int getDebt(UUID id) { return getData(id).getDebt(); }
    public void setDebt(UUID id, int amount) { getData(id).setDebt(amount); }
    public void addDebt(UUID id, int amount) { getData(id).addDebt(amount); }

    // --- ローンタイマー ---
    public long getLoanDeadline(UUID id) { return getData(id).getLoanDeadlineMillis(); }
    public void setLoanDeadline(UUID id, long millis) { getData(id).setLoanDeadlineMillis(millis); }

    public long getNextInterestMillis(UUID id) { return getData(id).getNextInterestMillis(); }
    public void setNextInterestMillis(UUID id, long millis) { getData(id).setNextInterestMillis(millis); }

    /**
     * ローンタイマーを完全リセットする（完済時に呼ぶ）。
     * debt は呼び出し元で 0 にしておくこと。
     */
    public void clearLoanTimer(UUID id) {
        getData(id).setLoanDeadlineMillis(0L);
        getData(id).setNextInterestMillis(0L);
    }
}
