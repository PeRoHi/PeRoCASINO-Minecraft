package me.bokan.perocasino.roulette;

import me.bokan.perocasino.economy.EconomyManager;
import me.bokan.perocasino.listeners.RouletteBetMenuListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 自動ルーレットの精算。
 *
 * 現在は「停止角度 → セグメント倍率」を採用し、総ベット×倍率を財布へ払い戻す。
 */
public final class RouletteSettlement {

    private RouletteSettlement() {}

    public record AngleRoundResult(int stopAngleDeg, int multiplier) {}

    /**
     * 0〜359°の停止角度をランダムに決め、その角度が属する倍率を結果として返す。
     * 角度一様乱数なので、セグメント幅（degrees）の比率がそのまま出現確率になる。
     */
    public static AngleRoundResult randomAngleResult(RouletteAngleConfig angleConfig) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int angle = r.nextInt(360);
        int mult = angleConfig.segmentForAngle(angle).multiplier();
        return new AngleRoundResult(angle, mult);
    }

    public static void settleRound(EconomyManager economy,
                                   RouletteBetMenuListener betListener,
                                   AngleRoundResult result,
                                   Location hub,
                                   double notifyRadius) {
        if (result == null) {
            throw new IllegalStateException("Roulette result が未設定です。");
        }

        Map<UUID, Inventory> open = betListener.getOpenBetInventoriesView();
        List<Map.Entry<UUID, Inventory>> snapshot = new ArrayList<>(open.entrySet());

        for (Map.Entry<UUID, Inventory> entry : snapshot) {
            UUID uuid = entry.getKey();
            Inventory inv = entry.getValue();
            if (inv == null) continue;

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            if (!player.getOpenInventory().getTopInventory().equals(inv)) continue;

            int totalBet = 0;

            for (int slot : RouletteBetMenuListener.BET_SLOTS) {
                ItemStack stack = inv.getItem(slot);
                if (stack == null || stack.getType() == Material.AIR) continue;
                if (stack.getType() != Material.DIAMOND) continue;
                totalBet += stack.getAmount();
            }

            int allIn = betListener.getAllInBets().getOrDefault(uuid, 0);
            totalBet += allIn;

            int payout = totalBet * result.multiplier();

            // 盤面のベット（ダイヤ以外も）を一旦クリア
            for (int slot : RouletteBetMenuListener.BET_SLOTS) {
                inv.setItem(slot, null);
            }
            betListener.getAllInBets().put(uuid, 0);
            betListener.refreshHiddenBundle(uuid, inv);

            if (payout > 0) {
                economy.addWalletBalance(uuid, payout);
                player.sendMessage("§a[ルーレット] §f結果: §e" + result.multiplier() + "x"
                        + " §7(角度 " + result.stopAngleDeg() + "°)"
                        + " §f| 払戻: §b" + payout + "§f（財布）");
            } else if (totalBet > 0) {
                player.sendMessage("§c[ルーレット] §f結果: §e" + result.multiplier() + "x"
                        + " §7(角度 " + result.stopAngleDeg() + "°)"
                        + " §f（払戻なし）");
            }

            // 開いている人だけ即時反映
            for (HumanEntity viewer : new ArrayList<>(inv.getViewers())) {
                if (viewer instanceof Player p) {
                    p.updateInventory();
                }
            }
        }

        // 近くにいるプレイヤーへ結果の一斉通知（GUIを開いていない人向け）
        if (hub != null && hub.getWorld() != null) {
            World w = hub.getWorld();
            double r2 = notifyRadius * notifyRadius;
            String msg = "§d[ルーレット] §f結果: §e" + result.multiplier() + "x §7(角度 " + result.stopAngleDeg() + "°)";
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getWorld().equals(w)) continue;
                if (p.getLocation().distanceSquared(hub) <= r2) {
                    p.sendMessage(msg);
                }
            }
        }
    }
}
