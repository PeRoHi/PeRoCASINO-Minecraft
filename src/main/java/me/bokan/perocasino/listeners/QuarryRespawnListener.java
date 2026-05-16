package me.bokan.perocasino.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 採石場内のダイヤモンド鉱石を掘ったら丸石に置換し、一定時間後に元に戻す。
 */
public class QuarryRespawnListener implements Listener {

    private final JavaPlugin plugin;
    private final Map<String, BukkitTask> pending = new ConcurrentHashMap<>();

    public QuarryRespawnListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("quarry.enabled", true)) return;

        Block block = event.getBlock();
        Material type = block.getType();
        if (type != Material.DIAMOND_ORE && type != Material.DEEPSLATE_DIAMOND_ORE && type != Material.COBBLESTONE) return;

        Location loc = block.getLocation();
        if (!isInQuarry(cfg, loc)) return;

        String key = key(loc);

        // 採石場の丸石は掘れない（無ドロップ＆即補完）
        if (type == Material.COBBLESTONE) {
            event.setDropItems(false);
            event.setExpToDrop(0);
            event.setCancelled(true);
            // 念のため1tick後に丸石を補完（クライアント側の見た目ズレ対策）
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (loc.getWorld() == null) return;
                Block b = loc.getWorld().getBlockAt(loc);
                if (b.getType() != Material.COBBLESTONE) {
                    b.setType(Material.COBBLESTONE, false);
                }
            });
            return;
        }

        // 既に復帰待ちの座標なら二重登録しない
        if (pending.containsKey(key)) return;

        // ビースト鉱石（=ダイヤ鉱石）は「ビーストコイン（=ダイヤ）」を落として、ブロックだけ丸石に置換する
        event.setDropItems(false);
        event.setExpToDrop(0);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), new org.bukkit.inventory.ItemStack(Material.DIAMOND, 1));

        block.setType(Material.COBBLESTONE, true);

        long delay = Math.max(20L, cfg.getLong("quarry.respawn-delay-ticks", 6000L));
        World world = loc.getWorld();
        if (world == null) return;

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        Material restore = type;

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pending.remove(key);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) return;
            Block b = world.getBlockAt(x, y, z);
            if (b.getType() == Material.COBBLESTONE) {
                b.setType(restore, false);
            }
        }, delay);

        pending.put(key, task);
    }

    private static boolean isInQuarry(FileConfiguration cfg, Location loc) {
        String wCfg = cfg.getString("quarry.world", "");
        if (wCfg != null && !wCfg.isBlank()) {
            if (loc.getWorld() == null || !wCfg.equalsIgnoreCase(loc.getWorld().getName())) {
                return false;
            }
        }

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
}
