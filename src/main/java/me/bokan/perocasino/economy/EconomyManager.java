package me.bokan.perocasino.economy;

import me.bokan.perocasino.data.PlayerData;
import org.bukkit.entity.Player;

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

    /**
     * 財布から {@code amount} を徴収し、不足分を借金として計上する（HiLo の強制借入と同じ）。
     *
     * @return 借金として計上した額（0 なら借金なし）
     */
    public int takeBetFromWalletOrDebt(UUID playerId, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int wallet = getWalletBalance(playerId);
        int fromWallet = Math.min(wallet, amount);
        if (fromWallet > 0) {
            setWalletBalance(playerId, wallet - fromWallet);
        }
        int debt = amount - fromWallet;
        if (debt > 0) {
            addDebt(playerId, debt);
            long now = System.currentTimeMillis();
            if (getLoanDeadline(playerId) <= 0L) {
                setLoanDeadline(playerId, now + 24L * 60L * 60L * 1000L);
            }
            if (getNextInterestMillis(playerId) <= 0L) {
                setNextInterestMillis(playerId, now + 60L * 60L * 1000L);
            }
        }
        return debt;
    }

    /** {@link #takeBetFromWalletOrDebt(UUID, int)} を実行し、借金が発生したらプレイヤーへ通知する。 */
    public void takeBetFromWalletOrDebt(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        int debt = takeBetFromWalletOrDebt(player.getUniqueId(), amount);
        if (debt > 0) {
            player.sendMessage("§c財布不足のため §e" + debt + " §cを強制借入しました。");
        }
    }
}
