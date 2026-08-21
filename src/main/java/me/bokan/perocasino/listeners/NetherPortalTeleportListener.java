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
 * 仕様: config.yml の nether-portal.enabled が true の場合のみ有効。
 */
public class NetherPortalTeleportListener implements Listener {

    private final Plugin plugin;

    public NetherPortalTeleportListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        FileConfiguration cfg = plugin.getConfig();
        // nether-portal.* を優先し、旧キー portal-teleport.* は後方互換で読む
        boolean enabled = cfg.getBoolean("nether-portal.enabled",
                cfg.getBoolean("portal-teleport.enabled", false));
        if (!enabled) return;

        // ネザーポータル由来のみ
        if (event.getCause() != PlayerPortalEvent.TeleportCause.NETHER_PORTAL) return;

        String worldName = cfg.getString("nether-portal.to.world",
                cfg.getString("portal-teleport.to.world", ""));
        if (worldName == null || worldName.isBlank()) return;
        World w = Bukkit.getWorld(worldName);
        if (w == null) return;

        double x = cfg.contains("nether-portal.to.x")
                ? cfg.getDouble("nether-portal.to.x")
                : cfg.getDouble("portal-teleport.to.x");
        double y = cfg.contains("nether-portal.to.y")
                ? cfg.getDouble("nether-portal.to.y")
                : cfg.getDouble("portal-teleport.to.y");
        double z = cfg.contains("nether-portal.to.z")
                ? cfg.getDouble("nether-portal.to.z")
                : cfg.getDouble("portal-teleport.to.z");
        float yaw = (float) (cfg.contains("nether-portal.to.yaw")
                ? cfg.getDouble("nether-portal.to.yaw", 0.0)
                : cfg.getDouble("portal-teleport.to.yaw", 0.0));
        float pitch = (float) (cfg.contains("nether-portal.to.pitch")
                ? cfg.getDouble("nether-portal.to.pitch", 0.0)
                : cfg.getDouble("portal-teleport.to.pitch", 0.0));

        Location to = new Location(w, x, y, z, yaw, pitch);

        event.setCanCreatePortal(false);
        event.setTo(to);

        Player p = event.getPlayer();
        p.sendMessage("§d[ポータル] §f指定座標へテレポートしました。");
    }
}

