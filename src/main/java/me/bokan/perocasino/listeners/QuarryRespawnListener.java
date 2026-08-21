package me.bokan.perocasino.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 採石場内のダイヤモンド鉱石を掘ったら丸石に置換し、一定時間後に元に戻す。
 */
public class QuarryRespawnListener implements Listener {

    private final JavaPlugin plugin;
    private final Map<String, BukkitTask> pending = new ConcurrentHashMap<>();
    private final Map<String, PendingRestore> pendingData = new ConcurrentHashMap<>();

    public QuarryRespawnListener(JavaPlugin plugin) {
        this.plugin = plugin;
        loadPendingFromConfig();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("quarry.enabled", true)) return;

        Block block = event.getBlock();
        Material type = block.getType();
        Location loc = block.getLocation();
        if (!isInQuarry(cfg, loc)) return;

        // 生成された丸石は採掘させない（没収・ドロップなし）
        if (type == Material.COBBLESTONE) {
            event.setDropItems(false);
            event.setCancelled(true);
            return;
        }

        if (type != Material.DIAMOND_ORE && type != Material.DEEPSLATE_DIAMOND_ORE) return;

        String key = key(loc);
        // 既に復帰待ちの座標なら二重登録しない
        if (pending.containsKey(key)) return;

        // バニラ破壊はキャンセルし、こちらで報酬を出す（確実に丸石へ置換するため）
        event.setDropItems(false);
        event.setExpToDrop(0);
        event.setCancelled(true);

        block.setType(Material.COBBLESTONE, true);

        long delay = Math.max(20L, cfg.getLong("quarry.respawn-delay-ticks", 6000L));
        World world = loc.getWorld();
        if (world == null) return;

        // 報酬（ビーストコイン＝ダイヤ）を1個ドロップ
        world.dropItemNaturally(loc.add(0.5, 0.5, 0.5), new ItemStack(Material.DIAMOND, 1));

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        Material restore = type;

        long restoreAtMillis = System.currentTimeMillis() + (delay * 50L);
        pendingData.put(key, new PendingRestore(world.getName(), x, y, z, restore, restoreAtMillis));
        savePendingToConfig();

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pending.remove(key);
            tryRestore(key);
        }, delay);

        pending.put(key, task);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (pendingData.isEmpty()) return;
        String w = event.getWorld().getName();
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        // このチャンク内の pending を復元できるものだけ試す
        for (String k : new ArrayList<>(pendingData.keySet())) {
            PendingRestore pr = pendingData.get(k);
            if (pr == null) continue;
            if (!w.equals(pr.world)) continue;
            if ((pr.x >> 4) != cx || (pr.z >> 4) != cz) continue;
            tryRestore(k);
        }
    }

    private void tryRestore(String key) {
        PendingRestore pr = pendingData.get(key);
        if (pr == null) return;
        World world = plugin.getServer().getWorld(pr.world);
        if (world == null) return;

        // 時間未到なら次の機会へ
        if (System.currentTimeMillis() < pr.restoreAtMillis) return;

        if (!world.isChunkLoaded(pr.x >> 4, pr.z >> 4)) return;
        Block b = world.getBlockAt(pr.x, pr.y, pr.z);
        if (b.getType() == Material.COBBLESTONE) {
            b.setType(pr.restore, false);
        }
        pendingData.remove(key);
        savePendingToConfig();
    }

    private void loadPendingFromConfig() {
        FileConfiguration cfg = plugin.getConfig();
        List<String> list = cfg.getStringList("quarry.pending");
        if (list == null || list.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (String line : list) {
            try {
                // world:x:y:z|MATERIAL|restoreAtMillis
                String[] parts = line.split("\\|");
                if (parts.length < 3) continue;
                String[] xyz = parts[0].split(":");
                if (xyz.length != 4) continue;
                String world = xyz[0];
                int x = Integer.parseInt(xyz[1]);
                int y = Integer.parseInt(xyz[2]);
                int z = Integer.parseInt(xyz[3]);
                Material restore = Material.valueOf(parts[1]);
                long restoreAt = Long.parseLong(parts[2]);
                String key = world + ":" + x + ":" + y + ":" + z;
                pendingData.put(key, new PendingRestore(world, x, y, z, restore, restoreAt));

                long remainMillis = Math.max(0L, restoreAt - now);
                long remainTicks = Math.max(1L, remainMillis / 50L);
                BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    pending.remove(key);
                    tryRestore(key);
                }, remainTicks);
                pending.put(key, task);
            } catch (Exception ignored) {
            }
        }
    }

    private void savePendingToConfig() {
        FileConfiguration cfg = plugin.getConfig();
        List<String> out = new ArrayList<>();
        for (PendingRestore pr : pendingData.values()) {
            out.add(pr.world + ":" + pr.x + ":" + pr.y + ":" + pr.z + "|" + pr.restore.name() + "|" + pr.restoreAtMillis);
        }
        cfg.set("quarry.pending", out);
        plugin.saveConfig();
    }

    private static boolean isInQuarry(FileConfiguration cfg, Location loc) {
        String wCfg = cfg.getString("quarry.world", "");
        if (wCfg != null && !wCfg.isBlank()) {
            if (loc.getWorld() == null || !wCfg.equalsIgnoreCase(loc.getWorld().getName())) {
                return false;
            }
        }

        // config.yml は quarry.min.x / quarry.max.x（セクション）を想定
        if (!cfg.isSet("quarry.min.x") || !cfg.isSet("quarry.max.x")) {
            return false;
        }

        int minX = Math.min(cfg.getInt("quarry.min.x"), cfg.getInt("quarry.max.x"));
        int maxX = Math.max(cfg.getInt("quarry.min.x"), cfg.getInt("quarry.max.x"));
        int minY = Math.min(cfg.getInt("quarry.min.y"), cfg.getInt("quarry.max.y"));
        int maxY = Math.max(cfg.getInt("quarry.min.y"), cfg.getInt("quarry.max.y"));
        int minZ = Math.min(cfg.getInt("quarry.min.z"), cfg.getInt("quarry.max.z"));
        int maxZ = Math.max(cfg.getInt("quarry.min.z"), cfg.getInt("quarry.max.z"));

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    private static String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private record PendingRestore(String world, int x, int y, int z, Material restore, long restoreAtMillis) {}

    public void shutdown() {
        for (BukkitTask task : pending.values()) {
            if (task != null) task.cancel();
        }
        pending.clear();
        savePendingToConfig();
    }
}
