package me.bokan.perocasino.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public class RouletteInteractListener implements Listener {

    private final JavaPlugin plugin;
    private final RouletteBetMenuListener betMenuListener;

    public RouletteInteractListener(JavaPlugin plugin, RouletteBetMenuListener betMenuListener) {
        this.plugin = plugin;
        this.betMenuListener = betMenuListener;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.GRINDSTONE) return;
        if (!isHubGrindstone(block)) return;

        event.setCancelled(true);
        betMenuListener.openBetGui(event.getPlayer());
    }

    private boolean isHubGrindstone(Block block) {
        FileConfiguration cfg = plugin.getConfig();
        String worldName = cfg.getString("roulette.world", "");
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        World world = block.getWorld();
        if (world == null || !worldName.equals(world.getName())) {
            return false;
        }
        Location loc = block.getLocation();
        return loc.getBlockX() == cfg.getInt("roulette.x")
                && loc.getBlockY() == cfg.getInt("roulette.y")
                && loc.getBlockZ() == cfg.getInt("roulette.z");
    }
}
