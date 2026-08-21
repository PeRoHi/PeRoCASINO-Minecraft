package me.bokan.perocasino.listeners;

import me.bokan.perocasino.roulette.RouletteBetBoardService;
import me.bokan.perocasino.roulette.RoulettePhase;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Material;
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
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.GRINDSTONE) {
            return;
        }
        if (!isRegisteredRouletteGrindstone(block)) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (RouletteBetMenuListener.getHubPhase() != RoulettePhase.BETTING) {
            player.sendMessage("§cルーレット進行中はベットできません。");
            return;
        }

        // 要望: 砥石5列のどれを右クリックしても、列別GUIではなく 54枠ベットGUI を開く
        betMenuListener.openBetGui(player);
    }

    private boolean isRegisteredRouletteGrindstone(Block block) {
        if (betBoardService != null && betBoardService.isBetGrindstone(block)) {
            return true;
        }
        FileConfiguration cfg = plugin.getConfig();
        String worldName = cfg.getString("roulette.world", "");
        if (worldName == null || worldName.isBlank() || block.getWorld() == null) {
            return false;
        }
        if (!worldName.equals(block.getWorld().getName())) {
            return false;
        }
        return block.getX() == cfg.getInt("roulette.x")
                && block.getY() == cfg.getInt("roulette.y")
                && block.getZ() == cfg.getInt("roulette.z");
    }
}
