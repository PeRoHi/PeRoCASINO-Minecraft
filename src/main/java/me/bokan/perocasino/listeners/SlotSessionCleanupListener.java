package me.bokan.perocasino.listeners;

import me.bokan.perocasino.games.slot.SlotMachineService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SlotSessionCleanupListener implements Listener {

    private final SlotMachineService slotMachineService;

    public SlotSessionCleanupListener(SlotMachineService slotMachineService) {
        this.slotMachineService = slotMachineService;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!SlotMachineService.GUI_TITLE.equals(event.getView().getTitle())) return;
        slotMachineService.onGuiClose(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        slotMachineService.onQuit(event.getPlayer().getUniqueId());
    }
}
