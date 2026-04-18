package me.bokan.perocasino.tasks;

import me.bokan.perocasino.economy.EconomyManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 全プレイヤーのアクションバーに「手持ち | 財布 | 借金 | 期限」を
 * 1秒（20tick）ごとに表示するタスク。
 */
public class HudTask extends BukkitRunnable {

    private final EconomyManager economyManager;

    public HudTask(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            int inv     = countInvDiamonds(player);
            int wallet  = economyManager.getWalletBalance(player.getUniqueId());
            int debt    = economyManager.getDebt(player.getUniqueId());
            long deadline = economyManager.getLoanDeadline(player.getUniqueId());

            String timerStr;
            if (debt > 0 && deadline > 0) {
                long remainMs = deadline - now;
                if (remainMs <= 0) {
                    timerStr = "§c00:00";
                } else {
                    long s = remainMs / 1000;
                    timerStr = "§e" + String.format("%02d:%02d", s / 60, s % 60);
                }
            } else {
                timerStr = "§7--:--";
            }

            String msg = "§f手持ち: §e" + inv
                    + " §8| §b財布: §f" + wallet
                    + " §8| §c借金: §f" + debt
                    + " §8| §e期限: " + timerStr;

            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
        }
    }

    /** プレイヤーインベントリ（スロット0〜35）のダイヤ総数を返す。 */
    private int countInvDiamonds(Player player) {
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
