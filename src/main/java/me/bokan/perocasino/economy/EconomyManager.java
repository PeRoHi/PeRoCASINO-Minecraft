package me.bokan.perocasino.economy;

import me.bokan.perocasino.data.PlayerData;
import me.bokan.perocasino.data.PlayerDataStore;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーの経済データ（財布残高・借金）をメモリ上で管理するクラス。
 */
public class EconomyManager {

    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private final Set<UUID> loadFailed = ConcurrentHashMap.newKeySet();
    private PlayerDataStore store;

    public void attachStore(PlayerDataStore store) {
        this.store = store;
    }

    public boolean isLoadFailed(UUID playerId) {
        return loadFailed.contains(playerId);
    }

    public Set<UUID> loadFailedIds() {
        return Set.copyOf(loadFailed);
    }

    public void loadAll() {
        if (store == null) {
            return;
        }
        for (UUID id : store.listPlayerIds()) {
            PlayerDataStore.Result result = store.load(id);
            if (result.outcome() == PlayerDataStore.Outcome.FAILED) {
                loadFailed.add(id);
                playerDataMap.remove(id);
            } else if (result.outcome() == PlayerDataStore.Outcome.OK && result.data() != null) {
                playerDataMap.put(id, result.data());
                loadFailed.remove(id);
            }
        }
    }

    public void savePlayer(UUID playerId) {
        persist(playerId);
    }

    public void saveAll() {
        for (UUID id : playerDataMap.keySet()) {
            persist(id);
        }
    }

    /** プレイヤーデータを取得する。存在しない場合は新規作成して返す。読込失敗プレイヤーは null。 */
    public PlayerData getData(UUID playerId) {
        if (loadFailed.contains(playerId)) {
            return null;
        }
        return playerDataMap.computeIfAbsent(playerId, PlayerData::new);
    }

    public boolean hasData(UUID playerId) {
        return playerDataMap.containsKey(playerId);
    }

    // --- 財布残高 ---
    public int getWalletBalance(UUID id) {
        PlayerData data = getData(id);
        return data == null ? 0 : data.getWalletBalance();
    }

    public void setWalletBalance(UUID id, int amount) {
        PlayerData data = getData(id);
        if (data == null) {
            return;
        }
        data.setWalletBalance(amount);
        persist(id);
    }

    /** @return 入金／出金が受理されたか。拒否時は残高据え置き。 */
    public boolean addWalletBalance(UUID id, int amount) {
        PlayerData data = getData(id);
        if (data == null) {
            return false;
        }
        boolean ok = data.addWalletBalance(amount);
        if (ok) {
            persist(id);
        }
        return ok;
    }

    public boolean tryDepositWallet(UUID id, int amount) {
        PlayerData data = getData(id);
        if (data == null) {
            return false;
        }
        boolean ok = data.tryDepositWallet(amount);
        if (ok) {
            persist(id);
        }
        return ok;
    }

    public boolean tryWithdrawWallet(UUID id, int amount) {
        PlayerData data = getData(id);
        if (data == null) {
            return false;
        }
        boolean ok = data.tryWithdrawWallet(amount);
        if (ok) {
            persist(id);
        }
        return ok;
    }

    // --- 借金 ---
    public int getDebt(UUID id) {
        PlayerData data = getData(id);
        return data == null ? 0 : data.getDebt();
    }

    public void setDebt(UUID id, int amount) {
        PlayerData data = getData(id);
        if (data == null) {
            return;
        }
        data.setDebt(amount);
        persist(id);
    }

    public boolean addDebt(UUID id, int amount) {
        PlayerData data = getData(id);
        if (data == null) {
            return false;
        }
        boolean ok = data.addDebt(amount);
        if (ok) {
            persist(id);
        }
        return ok;
    }

    public boolean tryBorrow(UUID id, int amount) {
        PlayerData data = getData(id);
        if (data == null) {
            return false;
        }
        boolean ok = data.tryBorrow(amount);
        if (ok) {
            persist(id);
        }
        return ok;
    }

    /** @return 実際に返済した額 */
    public int tryRepay(UUID id, int amount) {
        PlayerData data = getData(id);
        if (data == null) {
            return 0;
        }
        int paid = data.tryRepay(amount);
        if (paid > 0) {
            persist(id);
        }
        return paid;
    }

    public int applyInterest(UUID id, int interest) {
        PlayerData data = getData(id);
        if (data == null) {
            return 0;
        }
        int debt = data.applyInterest(interest);
        persist(id);
        return debt;
    }

    // --- ローンタイマー ---
    public long getLoanDeadline(UUID id) {
        PlayerData data = getData(id);
        return data == null ? 0L : data.getLoanDeadlineMillis();
    }

    public void setLoanDeadline(UUID id, long millis) {
        PlayerData data = getData(id);
        if (data == null) {
            return;
        }
        data.setLoanDeadlineMillis(millis);
        persist(id);
    }

    public long getNextInterestMillis(UUID id) {
        PlayerData data = getData(id);
        return data == null ? 0L : data.getNextInterestMillis();
    }

    public void setNextInterestMillis(UUID id, long millis) {
        PlayerData data = getData(id);
        if (data == null) {
            return;
        }
        data.setNextInterestMillis(millis);
        persist(id);
    }

    /**
     * ローンタイマーを完全リセットする（完済時に呼ぶ）。
     * debt は呼び出し元で 0 にしておくこと。
     */
    public void clearLoanTimer(UUID id) {
        PlayerData data = getData(id);
        if (data == null) {
            return;
        }
        data.setLoanDeadlineMillis(0L);
        data.setNextInterestMillis(0L);
        persist(id);
    }

    private void persist(UUID id) {
        if (store == null || loadFailed.contains(id)) {
            return;
        }
        PlayerData data = playerDataMap.get(id);
        if (data == null) {
            return;
        }
        store.save(data, store.fileExists(id));
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
