package me.bokan.perocasino.listeners;

import me.bokan.perocasino.roulette.RouletteBetBoardService;
import me.bokan.perocasino.roulette.RoulettePhase;
import org.bukkit.Material;
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
        // 右クリックした時だけ反応
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // クリックしたブロックが砥石（GRINDSTONE）かチェック
            if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.GRINDSTONE) {
                // バニラの砥石画面が開くのを防ぐ
                event.setCancelled(true);

                Player player = event.getPlayer();
                // 物理ベット盤が有効なら、ベット受付中のみ許可
                if (betBoardService != null && betBoardService.isConfigured()) {
                    if (RouletteBetMenuListener.getHubPhase() != RoulettePhase.BETTING) {
                        player.sendMessage("§cルーレット進行中はベットできません。");
                        return;
                    }
                }
                // 物理ベット盤が設定されているならそちらを優先
                if (betBoardService != null && betBoardService.isBetGrindstone(event.getClickedBlock())) {
                    betBoardService.handleBetClick(player, event);
                    return;
                }

                // フォールバック: 従来GUI
                betMenuListener.openBetGui(player);
            }
        }
    }
}