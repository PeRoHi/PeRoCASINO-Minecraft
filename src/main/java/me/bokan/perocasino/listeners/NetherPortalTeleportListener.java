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
 * 仕様: config.yml の portal-teleport.enabled が true の場合のみ有効。
 */
public class NetherPortalTeleportListener implements Listener {

    private final Plugin plugin;

    public NetherPortalTeleportListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("portal-teleport.enabled", false)) return;

        // ネザーポータル由来のみ
        if (event.getCause() != PlayerPortalEvent.TeleportCause.NETHER_PORTAL) return;

        String worldName = cfg.getString("portal-teleport.to.world", "");
        if (worldName == null || worldName.isBlank()) return;
        World w = Bukkit.getWorld(worldName);
        if (w == null) return;

        double x = cfg.getDouble("portal-teleport.to.x");
        double y = cfg.getDouble("portal-teleport.to.y");
        double z = cfg.getDouble("portal-teleport.to.z");
        float yaw = (float) cfg.getDouble("portal-teleport.to.yaw", 0.0);
        float pitch = (float) cfg.getDouble("portal-teleport.to.pitch", 0.0);

        Location to = new Location(w, x, y, z, yaw, pitch);

        event.setCanCreatePortal(false);
        event.setTo(to);

        Player p = event.getPlayer();
        p.sendMessage("§d[ポータル] §f指定座標へテレポートしました。");
    }
}

