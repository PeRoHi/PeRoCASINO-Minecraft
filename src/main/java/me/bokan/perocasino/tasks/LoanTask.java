package me.bokan.perocasino.tasks;

import me.bokan.perocasino.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 借金（debt > 0）があるオンラインプレイヤーに対して
 * 5分ごとに 10% の利息を適用するタスク。
 *
 * 利息発生時に walletBalance == 0 かつインベントリにダイヤが無い場合は
 * triggerForcedLabor を呼び出す。
 * debt が 0 になったら即座にローンタイマーをリセットする。
 */
public class LoanTask extends BukkitRunnable {

    private static final long INTEREST_INTERVAL_MS = 5 * 60 * 1000L; // 5分

    private final EconomyManager economyManager;

    public LoanTask(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            var uuid = player.getUniqueId();
            int debt = economyManager.getDebt(uuid);
            if (debt <= 0) continue;

            long nextInterest = economyManager.getNextInterestMillis(uuid);
            if (nextInterest == 0 || now < nextInterest) continue;

            // 10% 利息（最低 +1）
            int interest = Math.max(1, (int) (debt * 0.10));
            economyManager.addDebt(uuid, interest);
            int newDebt = economyManager.getDebt(uuid);

            player.sendMessage("§c§l[利息] §f借金が §c" + debt + " §f→ §c" + newDebt + " §fになりました。");

            // 次回利息スケジュール
            economyManager.setNextInterestMillis(uuid, nextInterest + INTEREST_INTERVAL_MS);

            // 返済能力チェック
            if (economyManager.getWalletBalance(uuid) == 0 && countInvDiamonds(player) == 0) {
                triggerForcedLabor(player);
            }
        }
    }

    /**
     * 返済能力がないプレイヤーへのペナルティ処理。
     * 現在はメッセージ通知のみ。後で拡張すること。
     */
    public static void triggerForcedLabor(Player player) {
        player.sendTitle(
                "§c§l強制労働",
                "§e財布も手持ちもありません！",
                10, 70, 20
        );
        player.sendMessage("§c§l[強制労働] §f返済能力がないため、強制労働が開始されます！");
    }

    /** インベントリ（スロット0〜35）のダイヤ総数を返す。 */
    private static int countInvDiamonds(Player player) {
        int count = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < 36; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.DIAMOND) {
                count += item.getAmount();
            }
        }
        return count;
    }
}
