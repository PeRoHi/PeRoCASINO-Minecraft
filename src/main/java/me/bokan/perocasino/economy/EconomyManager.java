package me.bokan.perocasino.economy;

import me.bokan.perocasino.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーの経済データ（財布残高・借金）をメモリ上で管理するクラス。
 */
public class EconomyManager {

    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();

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

    /** @return 入金／出金が受理されたか。拒否時は残高据え置き。 */
    public boolean addWalletBalance(UUID id, int amount) {
        return getData(id).addWalletBalance(amount);
    }

    public boolean tryDepositWallet(UUID id, int amount) {
        return getData(id).tryDepositWallet(amount);
    }

    public boolean tryWithdrawWallet(UUID id, int amount) {
        return getData(id).tryWithdrawWallet(amount);
    }

    // --- 借金 ---
    public int getDebt(UUID id) { return getData(id).getDebt(); }
    public void setDebt(UUID id, int amount) { getData(id).setDebt(amount); }
    public boolean addDebt(UUID id, int amount) { return getData(id).addDebt(amount); }

    public boolean tryBorrow(UUID id, int amount) {
        return getData(id).tryBorrow(amount);
    }

    /** @return 実際に返済した額 */
    public int tryRepay(UUID id, int amount) {
        return getData(id).tryRepay(amount);
    }

    public int applyInterest(UUID id, int interest) {
        return getData(id).applyInterest(interest);
    }

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
     * ダイヤをインベントリへ渡し、入り切らなかった分は財布へ戻す。
     * オフラインなら全額財布。財布も溢れる分は足元に落とす。
     */
    public boolean giveDiamondsOrWallet(Player player, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (player == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        if (!player.isOnline()) {
            return tryDepositWallet(id, amount);
        }
        int remaining = amount;
        while (remaining > 0) {
            int n = Math.min(64, remaining);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(Material.DIAMOND, n));
            if (!leftover.isEmpty()) {
                int notFit = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                int toWallet = notFit + (remaining - n);
                if (tryDepositWallet(id, toWallet)) {
                    return true;
                }
                for (ItemStack extra : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), extra);
                }
                int notAttempted = remaining - n;
                dropDiamonds(player, notAttempted);
                return false;
            }
            remaining -= n;
        }
        return true;
    }

    private static void dropDiamonds(Player player, int amount) {
        int left = amount;
        while (left > 0) {
            int n = Math.min(64, left);
            player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(Material.DIAMOND, n));
            left -= n;
        }
    }
}
