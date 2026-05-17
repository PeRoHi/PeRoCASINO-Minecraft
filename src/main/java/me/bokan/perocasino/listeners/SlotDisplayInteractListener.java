package me.bokan.perocasino.listeners;

import me.bokan.perocasino.games.slotdisplay.SlotDisplayKeys;
import me.bokan.perocasino.games.slotdisplay.SlotDisplayMachine;
import me.bokan.perocasino.games.slotdisplay.SlotDisplayService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Interaction（スピン／ストップ）のクリックを処理する。
 */
public final class SlotDisplayInteractListener implements Listener {

    private final SlotDisplayService slotDisplayService;

    public SlotDisplayInteractListener(SlotDisplayService slotDisplayService) {
        this.slotDisplayService = slotDisplayService;
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Interaction)) return;

        SlotDisplayKeys keys = slotDisplayService.keys();
        if (!entity.getPersistentDataContainer().has(keys.machineIdKey(), PersistentDataType.STRING)) {
            return;
        }
        String machineId = entity.getPersistentDataContainer().get(keys.machineIdKey(), PersistentDataType.STRING);
        String role = entity.getPersistentDataContainer().get(keys.roleKey(), PersistentDataType.STRING);
        if (machineId == null || role == null) return;

        SlotDisplayMachine machine = slotDisplayService.getMachine(machineId.toLowerCase(Locale.ROOT));
        if (machine == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (SlotDisplayKeys.roleSpin().equals(role)) {
            machine.trySpin(player);
        } else if (role.startsWith("stop:")) {
            String idxStr = role.substring("stop:".length());
            try {
                int idx = Integer.parseInt(idxStr);
                machine.tryStopReel(player, idx);
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
    }
}
