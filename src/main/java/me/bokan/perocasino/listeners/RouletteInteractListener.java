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

    public RouletteInteractListener(RouletteBetMenuListener betMenuListener, RouletteBetBoardService betBoardService) {
        this.betMenuListener = betMenuListener;
        this.betBoardService = betBoardService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        // ルーレット拠点・ベット盤の砥石のみ反応（ワールド中の全砥石には反応しない）
        if (!betBoardService.isAnyRouletteGrindstone(clicked)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (RouletteBetMenuListener.getHubPhase() != RoulettePhase.BETTING) {
            player.sendMessage("§cルーレット進行中はベットできません。");
            return;
        }

        // 5列盤の砥石も含め、54枠ベットGUIを開く（列別GUIは RouletteBetBoardMenuListener 経由で別途利用可）
        betMenuListener.openBetGui(player);
    }
}
