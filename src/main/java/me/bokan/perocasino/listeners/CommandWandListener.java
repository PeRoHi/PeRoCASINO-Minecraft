package me.bokan.perocasino.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * メインハンドのコマンド杖（既定: 人参付きの棒）を右クリックしたとき、
 * アイテムの表示名に応じたコマンドを {@code config.yml} の {@code command-wand.wands} から実行する。
 */
public final class CommandWandListener implements Listener {

    /** 表示名が {@code tp-100-64-200} のとき座標テレポート */
    private static final Pattern TELEPORT_NAME = Pattern.compile("^tp-(-?\\d+)-(-?\\d+)-(-?\\d+)$", Pattern.CASE_INSENSITIVE);

    private final JavaPlugin plugin;
    private final ConcurrentHashMap<UUID, Long> lastUseMillis = new ConcurrentHashMap<>();

    public CommandWandListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("command-wand.enabled", false)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        if (!matchesMaterial(main, cfg)) {
            return;
        }

        String perm = cfg.getString("command-wand.permission", "perocasino.commandwand");
        if (perm != null && !perm.isBlank() && !player.hasPermission(perm)) {
            return;
        }

        if (cfg.getBoolean("command-wand.skip-when-riding-pig", true)
                && player.getVehicle() instanceof Pig) {
            return;
        }

        String displayName = plainDisplayName(main);
        List<String> resolved = resolveCommands(cfg, displayName, player);
        if (resolved.isEmpty()) {
            return;
        }

        List<String> allowed = cfg.getStringList("command-wand.allowed-command-labels");
        if (allowed == null || allowed.isEmpty()) {
            player.sendMessage("§c[PeRoCasino] command-wand.allowed-command-labels が空です。");
            return;
        }

        List<String> toRun = filterAllowed(resolved, allowed, player);
        if (toRun.isEmpty()) {
            return;
        }

        int cooldownTicks = Math.max(0, cfg.getInt("command-wand.cooldown-ticks", 20));
        long cooldownMs = cooldownTicks * 50L;
        long nowMs = System.currentTimeMillis();
        if (cooldownMs > 0) {
            Long prev = lastUseMillis.get(player.getUniqueId());
            if (prev != null && nowMs - prev < cooldownMs) {
                return;
            }
        }

        event.setCancelled(true);

        if (cooldownMs > 0) {
            lastUseMillis.put(player.getUniqueId(), nowMs);
        }

        Bukkit.getScheduler().runTask(plugin, () -> runCommands(player, toRun));
    }

    /**
     * 表示名に対応するコマンド列を解決する。
     * 1) {@code command-wand.wands.<表示名>} のリスト
     * 2) 表示名が {@code tp-x-y-z} 形式ならテレポート
     * 3) （互換）表示名未設定・未一致時のみ {@code command-wand.commands}
     */
    private List<String> resolveCommands(FileConfiguration cfg, String displayName, Player player) {
        List<String> out = new ArrayList<>();

        if (displayName != null && !displayName.isBlank()) {
            ConfigurationSection wands = cfg.getConfigurationSection("command-wand.wands");
            if (wands != null) {
                for (String key : wands.getKeys(false)) {
                    if (displayName.equals(plainKey(key))) {
                        out.addAll(expandPlaceholders(wands.getStringList(key), cfg, player));
                        return out;
                    }
                }
            }

            Matcher tp = TELEPORT_NAME.matcher(displayName);
            if (tp.matches()) {
                out.add("tp " + tp.group(1) + " " + tp.group(2) + " " + tp.group(3));
                return out;
            }
        }

        // 旧形式: 名前なし・未登録の杖だけ共通 commands を使う（既存設定を消さない）
        if (displayName == null || displayName.isBlank()) {
            List<String> legacy = cfg.getStringList("command-wand.commands");
            if (legacy != null) {
                out.addAll(expandPlaceholders(legacy, cfg, player));
            }
        }
        return out;
    }

    private static List<String> expandPlaceholders(List<String> raw, FileConfiguration cfg, Player player) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        String slotId = cfg.getString("command-wand.slot-create-id", "wand_slot");
        for (String line : raw) {
            if (line == null) {
                continue;
            }
            String s = line.trim()
                    .replace("{slot_id}", slotId)
                    .replace("{player}", player.getName());
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static String plainDisplayName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) {
            return null;
        }
        return ChatColor.stripColor(meta.getDisplayName()).trim();
    }

    private static String plainKey(String configKey) {
        return ChatColor.stripColor(configKey).trim();
    }

    private List<String> filterAllowed(List<String> commands, List<String> allowedLabels, Player player) {
        List<String> toRun = new ArrayList<>();
        for (String raw : commands) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("/")) {
                line = line.substring(1);
            }
            if (!isAllowedRoot(line, allowedLabels)) {
                player.sendMessage("§c[PeRoCasino] コマンド杖: 許可されていないコマンドです: §f" + line);
                continue;
            }
            toRun.add(line);
        }
        return toRun;
    }

    private void runCommands(Player player, List<String> toRun) {
        int between = Math.max(1, plugin.getConfig().getInt("command-wand.delay-ticks-between-commands", 1));
        int nextDelay = 0;
        boolean first = true;
        for (String dispatch : toRun) {
            if (first) {
                dispatchLine(player, dispatch);
                first = false;
                nextDelay = between;
            } else {
                final int when = nextDelay;
                final String d = dispatch;
                Bukkit.getScheduler().runTaskLater(plugin, () -> dispatchLine(player, d), when);
                nextDelay += between;
            }
        }
    }

    private static void dispatchLine(Player player, String dispatch) {
        try {
            Bukkit.dispatchCommand(player, dispatch);
        } catch (Throwable t) {
            player.sendMessage("§c[PeRoCasino] コマンド実行に失敗しました: §f" + dispatch);
        }
    }

    private static boolean isAllowedRoot(String commandLine, List<String> allowedLabels) {
        String[] parts = commandLine.split("\\s+");
        if (parts.length == 0) {
            return false;
        }
        String root = parts[0].toLowerCase(Locale.ROOT);
        for (String label : allowedLabels) {
            if (label == null || label.isBlank()) {
                continue;
            }
            if (root.equals(label.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesMaterial(ItemStack item, FileConfiguration cfg) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        String matName = cfg.getString("command-wand.material", "CARROT_ON_A_STICK");
        Material mat;
        try {
            mat = Material.valueOf(matName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (item.getType() != mat) {
            return false;
        }
        if (!cfg.getBoolean("command-wand.match-custom-model-data", false)) {
            return true;
        }
        if (!item.hasItemMeta() || !item.getItemMeta().hasCustomModelData()) {
            return false;
        }
        return item.getItemMeta().getCustomModelData() == cfg.getInt("command-wand.custom-model-data", 0);
    }
}
