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
 * 自動ルーレットの簡易精算（ベット枠の鉱石アイテムと、ランダムに出た3つの鉱石結果を突き合わせる）。
 */
public final class RouletteSettlement {

    private RouletteSettlement() {}

    public record RoundResult(Material a, Material b, Material c) {}

    public static RoundResult randomResult(List<Material> symbolPool) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Material a = symbolPool.get(r.nextInt(symbolPool.size()));
        Material b = symbolPool.get(r.nextInt(symbolPool.size()));
        Material c = symbolPool.get(r.nextInt(symbolPool.size()));
        return new RoundResult(a, b, c);
    }

    public static void settleRound(EconomyManager economy,
                                   RouletteBetMenuListener betListener,
                                   List<Material> symbolPool,
                                   int payoutThree,
                                   int payoutTwo,
                                   Location hub,
                                   double notifyRadius) {
        RoundResult result = randomResult(symbolPool);

        Map<UUID, Inventory> open = betListener.getOpenBetInventoriesView();
        List<Map.Entry<UUID, Inventory>> snapshot = new ArrayList<>(open.entrySet());

        for (Map.Entry<UUID, Inventory> entry : snapshot) {
            UUID uuid = entry.getKey();
            Inventory inv = entry.getValue();
            if (inv == null) continue;

            Player player = Bukkit.getPlayer(uuid);

            int totalBet = 0;
            int matches = 0;

            for (int slot : RouletteBetMenuListener.BET_SLOTS) {
                ItemStack stack = inv.getItem(slot);
                if (stack == null || stack.getType() == Material.AIR) continue;
                if (stack.getType() != Material.DIAMOND) continue;
                totalBet += stack.getAmount();
            }

            int allIn = betListener.getAllInBets().getOrDefault(uuid, 0);
            long totalLong = (long) totalBet + (long) allIn;
            totalBet = totalLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalLong;

            for (int slot : RouletteBetMenuListener.BET_SLOTS) {
                ItemStack stack = inv.getItem(slot);
                if (stack == null || stack.getType() == Material.AIR) continue;
                if (stack.getType() == Material.DIAMOND) continue;
                Material m = stack.getType();
                if (m == result.a() || m == result.b() || m == result.c()) {
                    matches++;
                }
            }

            int mult = 0;
            if (matches >= 3) mult = payoutThree;
            else if (matches == 2) mult = payoutTwo;

            long payoutLong = (long) totalBet * (long) mult;
            int payout = payoutLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) payoutLong;

            for (int slot : RouletteBetMenuListener.BET_SLOTS) {
                inv.setItem(slot, null);
            }
            betListener.getAllInBets().put(uuid, 0);
            betListener.refreshHiddenBundle(uuid, inv);

            if (payout > 0) {
                if (!economy.tryDepositWallet(uuid, payout)) {
                    Bukkit.getLogger().warning("Roulette payout rejected (wallet overflow) uuid=" + uuid);
                } else if (player != null && player.isOnline()) {
                    player.sendMessage("§a[ルーレット] §f結果: §e" + shortName(result.a())
                            + " §7/ §e" + shortName(result.b())
                            + " §7/ §e" + shortName(result.c())
                            + " §f| 一致: §b" + matches
                            + " §f| 払戻: §b" + payout + "§f（財布）");
                }
            } else if (totalBet > 0 && player != null && player.isOnline()) {
                player.sendMessage("§c[ルーレット] §f結果: §e" + shortName(result.a())
                        + " §7/ §e" + shortName(result.b())
                        + " §7/ §e" + shortName(result.c())
                        + " §f| 一致: §7" + matches + " §f（払戻なし）");
            }

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
            String msg = "§d[ルーレット] §f結果: §e" + shortName(result.a())
                    + " §7/ §e" + shortName(result.b())
                    + " §7/ §e" + shortName(result.c());
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getWorld().equals(w)) continue;
                if (p.getLocation().distanceSquared(hub) <= r2) {
                    p.sendMessage(msg);
                }
            }
        }
    }

    private static String shortName(Material m) {
        String s = m.name().toLowerCase().replace('_', ' ');
        if (s.length() <= 14) return s;
        return s.substring(0, 14) + "…";
    }
}
