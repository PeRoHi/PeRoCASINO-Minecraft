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

    public static int computePayout(int totalBet, int matches, int payoutThree, int payoutTwo) {
        int mult = 0;
        if (matches >= 3) {
            mult = payoutThree;
        } else if (matches == 2) {
            mult = payoutTwo;
        }
        if (totalBet <= 0 || mult <= 0) {
            return 0;
        }
        long payoutLong = (long) totalBet * (long) mult;
        return payoutLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) payoutLong;
    }

    public static int countBetDiamonds(ItemStack[] contents) {
        int n = 0;
        if (contents == null) {
            return 0;
        }
        for (int slot : RouletteBetMenuListener.BET_SLOTS) {
            if (slot < 0 || slot >= contents.length) {
                continue;
            }
            ItemStack stack = contents[slot];
            if (stack != null && stack.getType() == Material.DIAMOND) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    public static int countOreMatches(ItemStack[] contents, RoundResult result) {
        int matches = 0;
        if (contents == null) {
            return 0;
        }
        for (int slot : RouletteBetMenuListener.BET_SLOTS) {
            if (slot < 0 || slot >= contents.length) {
                continue;
            }
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            if (stack.getType() == Material.DIAMOND) {
                continue;
            }
            Material m = stack.getType();
            if (m == result.a() || m == result.b() || m == result.c()) {
                matches++;
            }
        }
        return matches;
    }

    public static void settleRound(EconomyManager economy,
                                   RouletteBetMenuListener betListener,
                                   List<Material> symbolPool,
                                   int payoutThree,
                                   int payoutTwo,
                                   Location hub,
                                   double notifyRadius) {
        RoundResult result = randomResult(symbolPool);

        for (UUID uuid : betListener.idsForSettlement()) {
            ItemStack[] contents = betListener.boardContents(uuid);
            Player player = Bukkit.getPlayer(uuid);

            int totalBet = countBetDiamonds(contents);
            int allIn = betListener.getAllInBets().getOrDefault(uuid, 0);
            long totalLong = (long) totalBet + (long) allIn;
            totalBet = totalLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalLong;

            int matches = countOreMatches(contents, result);
            int payout = computePayout(totalBet, matches, payoutThree, payoutTwo);

            if (payout > 0 && !economy.creditPayout(uuid, player, payout)) {
                Bukkit.getLogger().warning("Roulette payout could not be credited; chips kept uuid=" + uuid);
                continue;
            }

            betListener.clearSettledBoard(uuid);

            if (payout > 0 && player != null && player.isOnline()) {
                player.sendMessage("§a[ルーレット] §f結果: §e" + shortName(result.a())
                        + " §7/ §e" + shortName(result.b())
                        + " §7/ §e" + shortName(result.c())
                        + " §f| 一致: §b" + matches
                        + " §f| 払戻: §b" + payout + "§f（財布／手持ち）");
            } else if (totalBet > 0 && player != null && player.isOnline()) {
                player.sendMessage("§c[ルーレット] §f結果: §e" + shortName(result.a())
                        + " §7/ §e" + shortName(result.b())
                        + " §7/ §e" + shortName(result.c())
                        + " §f| 一致: §7" + matches + " §f（払戻なし）");
            }

            Inventory open = betListener.getOpenBetInventoriesView().get(uuid);
            if (open != null) {
                for (HumanEntity viewer : new ArrayList<>(open.getViewers())) {
                    if (viewer instanceof Player p) {
                        p.updateInventory();
                    }
                }
            }
        }

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
