package me.bokan.perocasino.listeners;

import me.bokan.perocasino.roulette.RouletteBetBoardService;
import me.bokan.perocasino.roulette.RoulettePhase;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

public class RouletteInteractListener implements Listener {

    private final Plugin plugin;
    private final RouletteBetMenuListener betMenuListener;
    private final RouletteBetBoardService betBoardService;

    public RouletteInteractListener(Plugin plugin, RouletteBetMenuListener betMenuListener,
                                    RouletteBetBoardService betBoardService) {
        this.plugin = plugin;
        this.betMenuListener = betMenuListener;
        this.betBoardService = betBoardService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.GRINDSTONE) return;

        boolean isHub = isHubGrindstone(block);
        boolean isBoard = betBoardService.isBetGrindstone(block);
        if (!isHub && !isBoard) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (RouletteBetMenuListener.getHubPhase() != RoulettePhase.BETTING) {
            player.sendMessage("§cルーレット進行中はベットできません。");
            return;
        }

        if (isBoard) {
            betBoardService.handleBetClick(player, event);
            return;
        }

        betMenuListener.openBetGui(player);
    }

    private boolean isHubGrindstone(Block block) {
        FileConfiguration cfg = plugin.getConfig();
        String worldName = cfg.getString("roulette.world", "");
        if (worldName == null || worldName.isBlank()) return false;
        World world = Bukkit.getWorld(worldName);
        if (world == null || !world.equals(block.getWorld())) return false;
        return block.getX() == cfg.getInt("roulette.x")
                && block.getY() == cfg.getInt("roulette.y")
                && block.getZ() == cfg.getInt("roulette.z");
    }
}
