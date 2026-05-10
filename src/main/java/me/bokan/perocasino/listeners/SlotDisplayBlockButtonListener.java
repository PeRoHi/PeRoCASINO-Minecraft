package me.bokan.perocasino.listeners;

import me.bokan.perocasino.games.slotdisplay.SlotDisplayKeys;
import me.bokan.perocasino.games.slotdisplay.SlotDisplayMachine;
import me.bokan.perocasino.games.slotdisplay.SlotDisplayService;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * ブロックのボタン（*_BUTTON）押下で、設置スロットの spin/stop を動かす。
 * 押したボタンは押下タイミングで消失（AIR）し、ラウンド終了時に復元する。
 */
public final class SlotDisplayBlockButtonListener implements Listener {

    private final SlotDisplayService slotDisplayService;

    public SlotDisplayBlockButtonListener(SlotDisplayService slotDisplayService) {
        this.slotDisplayService = slotDisplayService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        String name = clicked.getType().name();
        if (!name.endsWith("_BUTTON")) return;

        Player player = event.getPlayer();

        // どの台のどのボタンかは、台の base + yaw から逆引き
        for (SlotDisplayMachine m : slotDisplayService.machinesView().values()) {
            String role = m.resolveBlockButtonRole(clicked);
            if (role == null) continue;

            event.setCancelled(true);

            if (SlotDisplayKeys.roleSpin().equals(role)) {
                boolean ok = m.trySpin(player);
                if (ok) {
                    m.removeButtonBlockForThisRound(clicked);
                }
                return;
            }

            if (role.startsWith("stop:")) {
                try {
                    int idx = Integer.parseInt(role.substring("stop:".length()));
                    boolean ok = m.tryStopReel(player, idx);
                    if (ok) {
                        m.removeButtonBlockForThisRound(clicked);
                    }
                } catch (NumberFormatException ignored) {
                    // ignore
                }
                return;
            }
        }
    }
}

