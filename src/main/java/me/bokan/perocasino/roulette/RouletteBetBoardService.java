package me.bokan.perocasino.roulette;

import me.bokan.perocasino.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

/**
 * ルーレットの「砥石5列ベット」管理。
 *
 * 仕様:
 * - 左から1〜5列目が 2/4/6/10/20 倍
 * - プレイヤーは手持ち(オフハンド含む)のビーストコイン(ダイヤ)を置く
 * - 結果倍率と一致した列に賭けていた分だけ: 掛け数×倍率 を返却
 *   - 返却は手持ちへ。入り切らない分は財布へ。
 * - 負けは没収（賭け分は戻らない）
 */
public final class RouletteBetBoardService {

    private static final int[] MULTS = new int[]{2, 4, 6, 10, 20};
    private static final String GUI_TITLE_PREFIX = "§0§lROULETTE BET ";

    private final Plugin plugin;
    private final EconomyManager economy;

    private Location origin; // 盤面左端の砥石（列1）の座標
    private String facing; // "NORTH" "SOUTH" "EAST" "WEST"（盤面が伸びる方向）

    /** ベット: player -> (multiplier -> amount) */
    private final Map<UUID, Map<Integer, Integer>> bets = new HashMap<>();

    /** プレイヤーごとの「列別ベットGUI」(multiplier -> inventory) */
    private final Map<UUID, Map<Integer, Inventory>> openBetGuis = new HashMap<>();

    public RouletteBetBoardService(Plugin plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        FileConfiguration cfg = plugin.getConfig();
        String wName = cfg.getString("roulette.board.world", "");
        if (wName == null || wName.isBlank()) {
            origin = null;
            facing = "EAST";
            return;
        }
        World w = Bukkit.getWorld(wName);
        if (w == null) {
            origin = null;
            facing = "EAST";
            return;
        }
        int x = cfg.getInt("roulette.board.x", 0);
        int y = cfg.getInt("roulette.board.y", 0);
        int z = cfg.getInt("roulette.board.z", 0);
        origin = new Location(w, x, y, z);
        facing = cfg.getString("roulette.board.facing", "EAST");
        if (!java.util.Set.of("NORTH", "SOUTH", "EAST", "WEST").contains(facing.toUpperCase())) {
            facing = "EAST";
        }
    }

    /**
     * プレイヤーの yaw（向いている方角）から、盤面の「列1→列5が伸びる方向」を返す。
     * 盤面の左端砥石を見たときに、右方向へ並んでいる方向を想定。
     */
    public static org.bukkit.block.BlockFace facingFromPlayerYaw(float yaw) {
        // Bukkit yaw は -180..180 付近。0=South, 90=West, 180/-180=North, -90=East
        float y = yaw % 360f;
        if (y < 0) y += 360f;
        if (y >= 315f || y < 45f) return org.bukkit.block.BlockFace.EAST;   // South向きなら右はEast
        if (y < 135f) return org.bukkit.block.BlockFace.SOUTH;              // West向きなら右はSouth
        if (y < 225f) return org.bukkit.block.BlockFace.WEST;               // North向きなら右はWest
        return org.bukkit.block.BlockFace.NORTH;                            // East向きなら右はNorth
    }

    public boolean isConfigured() {
        return origin != null && origin.getWorld() != null;
    }

    public boolean isBetGrindstone(Block block) {
        if (!isConfigured() || block == null) return false;
        if (block.getType() != Material.GRINDSTONE) return false;
        Location l = block.getLocation();
        if (!l.getWorld().equals(origin.getWorld())) return false;
        int dx = l.getBlockX() - origin.getBlockX();
        int dy = l.getBlockY() - origin.getBlockY();
        int dz = l.getBlockZ() - origin.getBlockZ();
        if (dy != 0) return false;
        int idx = indexFromDelta(dx, dz);
        return idx >= 0 && idx < 5;
    }

    public void handleBetClick(Player player, PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        int mult = multiplierFor(event.getClickedBlock());
        if (mult <= 0) return;
        openBetGui(player, mult);
    }

    public boolean isBetGui(Inventory inv) {
        if (inv == null) return false;
        return isBetGuiTitle(inv.getViewers().isEmpty() ? null : inv.getViewers().getFirst().getOpenInventory().getTitle());
    }

    public boolean isBetGuiTitle(String title) {
        return title != null && title.startsWith(GUI_TITLE_PREFIX);
    }

    public int multiplierFromGuiTitle(String title) {
        if (title == null) return 0;
        // "§0§lROULETTE BET 10x"
        String plain = org.bukkit.ChatColor.stripColor(title);
        if (plain == null) return 0;
        plain = plain.trim();
        if (!plain.startsWith("ROULETTE BET")) return 0;
        int idx = plain.lastIndexOf(' ');
        if (idx < 0) return 0;
        String last = plain.substring(idx + 1).trim();
        if (last.endsWith("x")) last = last.substring(0, last.length() - 1);
        try {
            return Integer.parseInt(last);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void onBetGuiClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        if (!isBetGui(inv)) return;
        int mult = multiplierFromGuiTitle(event.getView().getTitle());
        if (mult <= 0) return;
        // 保存: GUI内のダイヤ枚数をベットとして記録（それ以外は強制排出して返す）
        int diamonds = 0;
        for (ItemStack it : inv.getContents()) {
            if (it == null) continue;
            if (it.getType() == org.bukkit.Material.DIAMOND) diamonds += it.getAmount();
            else {
                // 想定外アイテムは返す
                player.getInventory().addItem(it);
            }
        }
        bets.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        bets.get(player.getUniqueId()).put(mult, diamonds);

        // GUIは都度作り直しせず保持しない（クローズ時に破棄）
        Map<Integer, Inventory> m = openBetGuis.get(player.getUniqueId());
        if (m != null) {
            m.remove(mult);
            if (m.isEmpty()) openBetGuis.remove(player.getUniqueId());
        }
    }

    private void openBetGui(Player player, int multiplier) {
        openBetGuis.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        Inventory inv = openBetGuis.get(player.getUniqueId()).get(multiplier);
        if (inv == null) {
            inv = Bukkit.createInventory(null, 27, "§0§lROULETTE BET " + multiplier + "x");
            // 既にベットがあれば表示
            int existing = bets.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(multiplier, 0);
            if (existing > 0) {
                inv.setItem(13, new ItemStack(org.bukkit.Material.DIAMOND, Math.min(64, existing)));
                int remain = existing - Math.min(64, existing);
                if (remain > 0) {
                    inv.setItem(14, new ItemStack(org.bukkit.Material.DIAMOND, Math.min(64, remain)));
                }
            }
            // ガイド
            ItemStack guide = new ItemStack(org.bukkit.Material.PAPER);
            ItemMeta meta = guide.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e置いたダイヤがベットになります");
                meta.setLore(java.util.List.of(
                        "§7このGUIにダイヤを置いて閉じるとベット確定",
                        "§7結果が出ると当たり列だけ払戻",
                        "§7外れは没収"
                ));
                guide.setItemMeta(meta);
            }
            inv.setItem(22, guide);
            openBetGuis.get(player.getUniqueId()).put(multiplier, inv);
        }
        player.openInventory(inv);
    }

    /**
     * GUI の内容をベットとして確定し、GUI内のダイヤは没収（=保管）する。
     * - close時に呼ぶ想定
     */
    public void captureBetFromGui(UUID playerId, Inventory topInv, String title) {
        int mult = multiplierFromGuiTitle(title);
        if (mult <= 0) return;
        int diamonds = countDiamonds(topInv);
        bets.computeIfAbsent(playerId, k -> new HashMap<>());
        bets.get(playerId).put(mult, diamonds);
        // GUI内はクリア（置きっぱなし防止）
        clearInventory(topInv);
    }

    private static int countDiamonds(Inventory inv) {
        int n = 0;
        for (ItemStack it : inv.getContents()) {
            if (it != null && it.getType() == Material.DIAMOND) n += it.getAmount();
        }
        return n;
    }

    private static void clearInventory(Inventory inv) {
        if (inv == null) return;
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, null);
        }
    }

    public int multiplierFor(Block block) {
        Location l = block.getLocation();
        int dx = l.getBlockX() - origin.getBlockX();
        int dz = l.getBlockZ() - origin.getBlockZ();
        int idx = indexFromDelta(dx, dz);
        if (idx < 0 || idx >= 5) return 0;
        return MULTS[idx];
    }

    private int indexFromDelta(int dx, int dz) {
        // facing は「列1→列5へ伸びる方向」
        return switch (facing.toUpperCase()) {
            case "EAST" -> (dz == 0) ? dx : -1;
            case "WEST" -> (dz == 0) ? -dx : -1;
            case "SOUTH" -> (dx == 0) ? dz : -1;
            case "NORTH" -> (dx == 0) ? -dz : -1;
            default -> (dz == 0) ? dx : -1;
        };
    }

    /**
     * 砥石を右クリックした時に、手持ちのダイヤを1つ賭ける（シフトなら最大64）。
     */
    public void placeBet(Player player, int multiplier, boolean shift) {
        if (multiplier <= 0) return;
        int take = shift ? 64 : 1;

        // メインハンド優先で回収、足りなければオフハンド
        int removed = removeDiamondsFromHands(player, take);
        if (removed <= 0) {
            player.sendMessage("§cビーストコイン（ダイヤ）がありません。");
            return;
        }

        bets.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        Map<Integer, Integer> m = bets.get(player.getUniqueId());
        m.put(multiplier, m.getOrDefault(multiplier, 0) + removed);
        player.sendMessage("§a[ルーレット] §f倍率 §e" + multiplier + "x §fに §b" + removed + " §f枚ベットしました。");
    }

    /**
     * そのプレイヤーのベットを精算する。
     */
    public void settleFor(Player player, int resultMultiplier) {
        Map<Integer, Integer> m = bets.remove(player.getUniqueId());
        if (m == null || m.isEmpty()) return;

        int betOnResult = m.getOrDefault(resultMultiplier, 0);
        if (betOnResult <= 0) {
            player.sendMessage("§c[ルーレット] §f結果: §e" + resultMultiplier + "x §f（はずれ / 没収）");
            return;
        }

        int payout = betOnResult * resultMultiplier;
        int toInv = addDiamondsToInventory(player, payout);
        int overflow = payout - toInv;
        if (overflow > 0) {
            economy.addWalletBalance(player.getUniqueId(), overflow);
        }
        player.sendMessage("§a[ルーレット] §f結果: §e" + resultMultiplier + "x §f当たり！ §f払戻 §b" + payout
                + "§f（手持ち+" + toInv + (overflow > 0 ? " / 財布+" + overflow : "") + "）");
    }

    /**
     * 現在記録されている全プレイヤーのベットを一括精算する。
     * （SPINNING終了→停止演出完了のタイミングで呼ぶ想定）
     */
    public void settleAll(int resultMultiplier) {
        // snapshotして安全に走査
        for (UUID uuid : java.util.List.copyOf(bets.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                // オフラインは没収扱いでクリア
                bets.remove(uuid);
                continue;
            }
            settleFor(p, resultMultiplier);
        }
    }

    public void lockAllGuis(boolean lock) {
        // 現状は閉じる強制まではしない（ロックはリスナー側でクリックキャンセルする想定）
    }

    public void clearAllBets() {
        bets.clear();
    }

    public void clearBets(UUID playerId) {
        bets.remove(playerId);
    }

    private static int removeDiamondsFromHands(Player player, int wanted) {
        int removed = 0;
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && main.getType() == Material.DIAMOND) {
            int take = Math.min(wanted - removed, main.getAmount());
            main.setAmount(main.getAmount() - take);
            if (main.getAmount() <= 0) player.getInventory().setItemInMainHand(null);
            removed += take;
        }
        if (removed >= wanted) return removed;
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && off.getType() == Material.DIAMOND) {
            int take = Math.min(wanted - removed, off.getAmount());
            off.setAmount(off.getAmount() - take);
            if (off.getAmount() <= 0) player.getInventory().setItemInOffHand(null);
            removed += take;
        }
        return removed;
    }

    private static int addDiamondsToInventory(Player player, int amount) {
        if (amount <= 0) return 0;
        ItemStack stack = new ItemStack(Material.DIAMOND, amount);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        int left = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
        return amount - left;
    }
}

