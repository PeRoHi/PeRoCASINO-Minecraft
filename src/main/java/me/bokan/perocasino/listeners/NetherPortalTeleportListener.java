package me.bokan.perocasino.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.plugin.Plugin;

/**
 * ネザーポータルに入った瞬間、指定座標へテレポートする。
 * 仕様: config.yml の nether-portal（旧キー portal-teleport も可）が true の場合のみ有効。
 */
public class NetherPortalTeleportListener implements Listener {

    private final Plugin plugin;

    public NetherPortalTeleportListener(Plugin plugin) {
        this.plugin = plugin;
    }

    private static String configPrefix(FileConfiguration cfg) {
        if (cfg.contains("nether-portal")) {
            return "nether-portal";
        }
        return "portal-teleport";
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        FileConfiguration cfg = plugin.getConfig();
        String prefix = configPrefix(cfg);
        if (!cfg.getBoolean(prefix + ".enabled", false)) return;

        // ネザーポータル由来のみ
        if (event.getCause() != PlayerPortalEvent.TeleportCause.NETHER_PORTAL) return;

        String worldName = cfg.getString(prefix + ".to.world", "");
        if (worldName == null || worldName.isBlank()) return;
        World w = Bukkit.getWorld(worldName);
        if (w == null) return;

        double x = cfg.getDouble(prefix + ".to.x");
        double y = cfg.getDouble(prefix + ".to.y");
        double z = cfg.getDouble(prefix + ".to.z");
        float yaw = (float) cfg.getDouble(prefix + ".to.yaw", 0.0);
        float pitch = (float) cfg.getDouble(prefix + ".to.pitch", 0.0);

        Location to = new Location(w, x, y, z, yaw, pitch);

        event.setCanCreatePortal(false);
        event.setTo(to);

        Player p = event.getPlayer();
        p.sendMessage("§d[ポータル] §f指定座標へテレポートしました。");
    }
}

