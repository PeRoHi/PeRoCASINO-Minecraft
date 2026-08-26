package me.bokan.perocasino.listeners;

import me.bokan.perocasino.roulette.RouletteBetBoardService;
import me.bokan.perocasino.roulette.RoulettePhase;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class RouletteInteractListener implements Listener {

    private final RouletteBetMenuListener betMenuListener;
    private final RouletteBetBoardService betBoardService;

    public RouletteInteractListener(RouletteBetMenuListener betMenuListener,
                                    RouletteBetBoardService betBoardService) {
        this.betMenuListener = betMenuListener;
        this.betBoardService = betBoardService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        // ルーレット拠点・ベット盤の砥石のみ反応（ワールド中の全砥石には反応しない）
        if (betBoardService == null || !betBoardService.isAnyRouletteGrindstone(block)) {
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
}
