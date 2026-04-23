package me.bokan.perocasino.listeners;

import me.bokan.perocasino.games.blackjack.BlackjackMenu;
import me.bokan.perocasino.games.hilo.HiLoMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * ブラックジャック / ハイアンドローの骨組みGUIクリック。
 */
public class GameMenuListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (!BlackjackMenu.GUI_TITLE.equals(title) && !HiLoMenu.GUI_TITLE.equals(title)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        if (event.getSlot() == 22) {
            player.closeInventory();
        }
    }
}
