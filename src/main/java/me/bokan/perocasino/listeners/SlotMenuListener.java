package me.bokan.perocasino.listeners;

import me.bokan.perocasino.games.slot.SlotMachineService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/**
 * スロットGUIのクリック制御（ベット枠以外は持ち出し不可）。
 * スピン中はベット枠もロックする。
 */
public class SlotMenuListener implements Listener {

    private final SlotMachineService slotMachineService;

    public SlotMenuListener(SlotMachineService slotMachineService) {
        this.slotMachineService = slotMachineService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!SlotMachineService.GUI_TITLE.equals(event.getView().getTitle())) return;

        GuiSafety.cancelOutsideClick(event);
        if (GuiSafety.cancelExoticClicks(event)) {
            return;
        }

        if (GuiSafety.cancelUnsafeBottomMoves(event)) {
            return;
        }

        if (!GuiSafety.isTopInventory(event)) {
            return;
        }

        event.setCancelled(true);

        int topSlot = event.getSlot();
        boolean isBetSlot = (topSlot == 12 || topSlot == 14);
        if (!isBetSlot) {
            return;
        }

        if (slotMachineService.isSpinning(player.getUniqueId())) {
            return;
        }

        if (event.getClick() == ClickType.NUMBER_KEY) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();

        if (cursor != null && cursor.getType() != Material.AIR) {
            if (cursor.getType() != Material.DIAMOND) {
                return;
            }
            int amount = cursor.getAmount();
            if (clicked == null || clicked.getType() == Material.AIR) {
                int place = Math.min(64, amount);
                invSet(player, topSlot, new ItemStack(Material.DIAMOND, place));
                cursor.setAmount(amount - place);
                player.setItemOnCursor(cursor.getAmount() <= 0 ? null : cursor);
            } else if (clicked.getType() == Material.DIAMOND) {
                int room = 64 - clicked.getAmount();
                if (room <= 0) {
                    return;
                }
                int moved = Math.min(room, amount);
                clicked.setAmount(clicked.getAmount() + moved);
                invSet(player, topSlot, clicked);
                cursor.setAmount(amount - moved);
                player.setItemOnCursor(cursor.getAmount() <= 0 ? null : cursor);
            }
            return;
        }

        if (clicked != null && clicked.getType() == Material.DIAMOND) {
            player.setItemOnCursor(clicked.clone());
            invSet(player, topSlot, null);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!SlotMachineService.GUI_TITLE.equals(event.getView().getTitle())) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (slotMachineService.isSpinning(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < 0 || raw >= topSize) {
                continue;
            }
            if (raw != 12 && raw != 14) {
                event.setCancelled(true);
                return;
            }
            ItemStack stacked = event.getNewItems().get(raw);
            if (stacked != null && stacked.getType() != Material.DIAMOND) {
                event.setCancelled(true);
                return;
            }
            if (stacked != null && stacked.getAmount() > 64) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private static void invSet(Player player, int topSlot, ItemStack stack) {
        player.getOpenInventory().getTopInventory().setItem(topSlot, stack);
    }
}
