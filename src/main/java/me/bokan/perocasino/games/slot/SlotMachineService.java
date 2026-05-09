package me.bokan.perocasino.games.slot;

import me.bokan.perocasino.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 3リール式スロット（GUI表示 + 3つの停止ボタン）。
 *
 * 仕様:
 * - スピン開始時点で結果（各リールの停止シンボル）を先に決める
 * - スピン中はシンボルを順繰り表示
 * - 各リールは個別の「停止ブロック」右クリックで止める（先に決めた結果に揃える）
 */
public class SlotMachineService {

    public static final String GUI_TITLE = "§0§lSLOT MACHINE";

    private final JavaPlugin plugin;
    private final EconomyManager economy;

    private final List<Material> symbols = new ArrayList<>();

    private Location spinButton;
    private Location stop1;
    private Location stop2;
    private Location stop3;

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public SlotMachineService(JavaPlugin plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        FileConfiguration cfg = plugin.getConfig();
        symbols.clear();
        for (String s : cfg.getStringList("slot-machine.symbols")) {
            try {
                symbols.add(Material.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("無効なスロットシンボル: " + s);
            }
        }
        if (symbols.isEmpty()) {
            symbols.addAll(List.of(
                    Material.COAL_ORE,
                    Material.COPPER_ORE,
                    Material.IRON_ORE,
                    Material.GOLD_ORE,
                    Material.REDSTONE_ORE,
                    Material.LAPIS_ORE,
                    Material.EMERALD_ORE,
                    Material.DIAMOND_ORE
            ));
        }

        spinButton = readLoc(cfg, "slot-machine.buttons.spin");
        stop1 = readLoc(cfg, "slot-machine.buttons.stop1");
        stop2 = readLoc(cfg, "slot-machine.buttons.stop2");
        stop3 = readLoc(cfg, "slot-machine.buttons.stop3");
    }

    private static Location readLoc(FileConfiguration cfg, String base) {
        String world = cfg.getString(base + ".world", "");
        if (world == null || world.isBlank()) return null;
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        int x = cfg.getInt(base + ".x");
        int y = cfg.getInt(base + ".y");
        int z = cfg.getInt(base + ".z");
        return new Location(w, x, y, z);
    }

    public void openGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);
        inv.setItem(10, icon(Material.HOPPER, "§eベット枠（ダイヤ）", List.of("§7ここにダイヤを置いてください")));
        inv.setItem(13, icon(Material.NOTE_BLOCK, "§dスロット", List.of("§7下のボタンでスピン/停止")));
        inv.setItem(16, icon(Material.HOPPER, "§eベット枠（ダイヤ）", List.of("§7ここにダイヤを置いてください")));

        // ベット置き場（簡易: 12と14）
        inv.setItem(12, null);
        inv.setItem(14, null);

        player.openInventory(inv);
    }

    public void handleInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = event.getClickedBlock();
        if (b == null) return;
        Location loc = b.getLocation();

        Player player = event.getPlayer();

        if (sameBlock(loc, spinButton)) {
            event.setCancelled(true);
            startSpin(player);
            return;
        }

        Session s = sessions.get(player.getUniqueId());
        if (s == null) return;

        if (sameBlock(loc, stop1)) {
            event.setCancelled(true);
            s.stopReel(0, player);
        } else if (sameBlock(loc, stop2)) {
            event.setCancelled(true);
            s.stopReel(1, player);
        } else if (sameBlock(loc, stop3)) {
            event.setCancelled(true);
            s.stopReel(2, player);
        }
    }

    public void onGuiClose(UUID playerId) {
        Session s = sessions.remove(playerId);
        if (s != null) {
            s.cancelAnim();
        }
    }

    private void startSpin(Player player) {
        if (spinButton == null) {
            player.sendMessage("§cスロットのスピンボタン座標が未設定です（config.yml）。");
            return;
        }
        if (stop1 == null || stop2 == null || stop3 == null) {
            player.sendMessage("§cスロットの停止ボタン座標が未設定です（config.yml）。");
            return;
        }

        Session old = sessions.remove(player.getUniqueId());
        if (old != null) {
            old.cancelAnim();
        }

        if (!GUI_TITLE.equals(player.getOpenInventory().getTitle())) {
            openGui(player);
        }

        Inventory inv = player.getOpenInventory().getTopInventory();
        if (inv == null || !GUI_TITLE.equals(player.getOpenInventory().getTitle())) {
            player.sendMessage("§cスロットGUIを開けませんでした。もう一度お試しください。");
            return;
        }

        int bet = countBetDiamonds(inv);
        if (bet <= 0) {
            player.sendMessage("§cベットするダイヤをGUIに置いてください（スロット左右の空き枠）。");
            return;
        }

        int min = Math.max(1, plugin.getConfig().getInt("slot-machine.spin-min-ticks", 40));
        int max = Math.max(min, plugin.getConfig().getInt("slot-machine.spin-max-ticks", 120));

        Material r0 = pickSymbol();
        Material r1 = pickSymbol();
        Material r2 = pickSymbol();

        Session session = new Session(player.getUniqueId(), inv, bet, r0, r1, r2, min, max);
        sessions.put(player.getUniqueId(), session);
        session.start();
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.4f);
        player.sendMessage("§aスロット開始！ §7各停止ボタンでリールを止めてください。");
    }

    private Material pickSymbol() {
        return symbols.get(ThreadLocalRandom.current().nextInt(symbols.size()));
    }

    private static boolean sameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || b.getWorld() == null) return false;
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private static ItemStack icon(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private static int countBetDiamonds(Inventory inv) {
        int n = 0;
        for (int slot : List.of(12, 14)) {
            ItemStack st = inv.getItem(slot);
            if (st != null && st.getType() == Material.DIAMOND) {
                n += st.getAmount();
            }
        }
        return n;
    }

    private final class Session {
        private final UUID playerId;
        private final Inventory inv;
        private final int bet;
        private final Material[] result = new Material[3];
        private final boolean[] stopped = new boolean[3];
        private final int minDelayTicks;
        private final int maxDelayTicks;
        private final int[] scheduledStopAtTick = new int[] { -1, -1, -1 };
        private int tick;
        private BukkitTask anim;

        private Session(UUID playerId, Inventory inv, int bet, Material r0, Material r1, Material r2, int min, int max) {
            this.playerId = playerId;
            this.inv = inv;
            this.bet = bet;
            this.result[0] = r0;
            this.result[1] = r1;
            this.result[2] = r2;
            this.minDelayTicks = Math.max(0, min);
            this.maxDelayTicks = Math.max(this.minDelayTicks, max);
        }

        void start() {
            tick = 0;
            anim = new BukkitRunnable() {
                @Override
                public void run() {
                    Player p = Bukkit.getPlayer(playerId);
                    if (p == null || !p.isOnline()) {
                        cancelAnim();
                        sessions.remove(playerId);
                        return;
                    }
                    if (!GUI_TITLE.equals(p.getOpenInventory().getTitle())) {
                        cancelAnim();
                        sessions.remove(playerId);
                        return;
                    }

                    tick++;
                    renderSpinningFrame();

                    // 予約停止（停止ボタン押下後のランダムtick経過で停止）
                    for (int i = 0; i < 3; i++) {
                        if (!stopped[i] && scheduledStopAtTick[i] >= 0 && tick >= scheduledStopAtTick[i]) {
                            forceStop(i);
                        }
                    }

                    // 全停止で精算
                    if (stopped[0] && stopped[1] && stopped[2]) {
                        finish(p);
                        cancelAnim();
                        sessions.remove(playerId);
                    }
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }

        void stopReel(int idx, Player player) {
            if (stopped[idx]) {
                player.sendMessage("§eそのリールは既に停止しています。");
                return;
            }
            if (scheduledStopAtTick[idx] >= 0) {
                player.sendMessage("§e停止予約済みです（少し待つと止まります）。");
                return;
            }

            // 停止ボタンを押した時点から、ランダムtick後に止まるよう予約する
            int min = Math.max(0, minDelayTicks);
            int max = Math.max(min, maxDelayTicks);
            int delay = ThreadLocalRandom.current().nextInt(min, max + 1);
            scheduledStopAtTick[idx] = tick + delay;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            player.sendMessage("§a停止予約！ §7" + ((delay + 19) / 20) + "秒後に停止します。");
        }

        private void forceStop(int idx) {
            stopped[idx] = true;
            scheduledStopAtTick[idx] = -1;
            renderStopped(idx);
        }

        private void renderSpinningFrame() {
            int a = (tick / 2) % symbols.size();
            int b = (tick / 2 + 3) % symbols.size();
            int c = (tick / 2 + 6) % symbols.size();
            if (!stopped[0]) inv.setItem(11, new ItemStack(symbols.get(a)));
            if (!stopped[1]) inv.setItem(13, new ItemStack(symbols.get(b)));
            if (!stopped[2]) inv.setItem(15, new ItemStack(symbols.get(c)));
        }

        private void renderStopped(int idx) {
            int slot = switch (idx) {
                case 0 -> 11;
                case 1 -> 13;
                case 2 -> 15;
                default -> 13;
            };
            inv.setItem(slot, new ItemStack(result[idx]));
        }

        private void finish(Player player) {
            // ベットダイヤを回収
            for (int slot : List.of(12, 14)) {
                inv.setItem(slot, null);
            }

            int three = Math.max(0, plugin.getConfig().getInt("slot-machine.payouts.three-of-a-kind", 8));
            int two = Math.max(0, plugin.getConfig().getInt("slot-machine.payouts.two-of-a-kind", 2));

            boolean allEq = result[0] == result[1] && result[1] == result[2];
            boolean pair = !allEq && (result[0] == result[1] || result[1] == result[2] || result[0] == result[2]);

            int mult = 0;
            if (allEq) mult = three;
            else if (pair) mult = two;

            int payout = bet * mult;
            if (payout > 0) {
                economy.addWalletBalance(player.getUniqueId(), payout);
                player.sendMessage("§a[SLOT] §f結果: §e" + result[0] + " §7| §e" + result[1] + " §7| §e" + result[2]
                        + " §f| 払戻: §b" + payout + " §7(財布)");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
            } else {
                player.sendMessage("§c[SLOT] §f結果: §e" + result[0] + " §7| §e" + result[1] + " §7| §e" + result[2]
                        + " §f| はずれ");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            }
        }

        void cancelAnim() {
            if (anim != null) anim.cancel();
        }
    }
}
