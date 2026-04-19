package me.bokan.perocasino.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

public class RouletteInteractListener implements Listener {

    private final RouletteBetMenuListener betMenuListener;

    public RouletteInteractListener(RouletteBetMenuListener betMenuListener) {
        this.betMenuListener = betMenuListener;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // 右クリックした時だけ反応
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // クリックしたブロックが砥石（GRINDSTONE）かチェック
            if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.GRINDSTONE) {
                // バニラの砥石画面が開くのを防ぐ
                event.setCancelled(true);
                
                // ルーレットのベット画面を開く
                betMenuListener.openBetGui(event.getPlayer());
            }
        }
    }
}