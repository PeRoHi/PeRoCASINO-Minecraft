package me.bokan.perocasino.listeners;

import me.bokan.perocasino.games.slot.SlotMachineService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class SlotInteractListener implements Listener {

    private final SlotMachineService slotMachineService;

    public SlotInteractListener(SlotMachineService slotMachineService) {
        this.slotMachineService = slotMachineService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        slotMachineService.handleInteract(event);
    }
}
