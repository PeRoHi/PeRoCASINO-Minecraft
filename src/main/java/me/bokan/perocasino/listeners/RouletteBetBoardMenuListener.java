package me.bokan.perocasino.listeners;

import me.bokan.perocasino.roulette.RouletteBetBoardService;
import me.bokan.perocasino.roulette.RoulettePhase;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * 砥石ベット盤の「列ごとベットGUI」制御。
 * - BETTING中のみ操作可
 * - ダイヤ以外は置けない
 * - 閉じたらベット枚数として保存（実体アイテムは没収＝保管扱い）
 */
public class RouletteBetBoardMenuListener implements Listener {

    private final RouletteBetBoardService betBoardService;

    public RouletteBetBoardMenuListener(RouletteBetBoardService betBoardService) {
        this.betBoardService = betBoardService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!betBoardService.isBetGui(event.getView().getTopInventory())) return;

        if (RouletteBetMenuListener.getHubPhase() != RoulettePhase.BETTING) {
            event.setCancelled(true);
            return;
        }

        // 上側(砥石GUI)だけ制御。プレイヤーインベントリは触れるが、シフト移動は抑止
        int raw = event.getRawSlot();
        if (raw >= event.getView().getTopInventory().getSize()) {
            if (event.isShiftClick()) {
                ItemStack current = event.getCurrentItem();
                if (current != null && current.getType() == Material.DIAMOND) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        // 砥石GUI側
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        // ダイヤ以外を置く/入れ替え禁止
        if (cursor != null && cursor.getType() != Material.AIR && cursor.getType() != Material.DIAMOND) {
            event.setCancelled(true);
            return;
        }
        if (current != null && current.getType() != Material.AIR && current.getType() != Material.DIAMOND) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory top = event.getInventory();
        betBoardService.onBetGuiClose(event);
    }
}

