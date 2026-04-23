package me.bokan.perocasino.listeners;

import me.bokan.perocasino.games.slot.SlotMachineService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * スロットGUIのクリック制御（ベット枠以外は持ち出し不可）。
 */
public class SlotMenuListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!SlotMachineService.GUI_TITLE.equals(event.getView().getTitle())) return;

        int raw = event.getRawSlot();

        // プレイヤーインベントリ側は基本許可（ダイヤを持ってくるため）
        if (raw >= 27) {
            // ただしシフトクリックで変な場所に入らないよう、GUIへのシフト移動は制御する
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                ItemStack current = event.getCurrentItem();
                if (current != null && current.getType() == Material.DIAMOND) {
                    // 上位インベントリへのシフト移動は、ベット枠へだけ許可したいが判定が難しいので一旦キャンセル
                    // （ダイヤはドラッグで置いてもらう）
                    event.setCancelled(true);
                }
            }
            return;
        }

        // GUI上部
        event.setCancelled(true);

        int topSlot = event.getSlot();
        boolean isBetSlot = (topSlot == 12 || topSlot == 14);

        if (!isBetSlot) {
            return;
        }

        // ベット枠: ダイヤのみ入出し
        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();

        if (event.getClick() == ClickType.NUMBER_KEY) {
            event.setCancelled(true);
            return;
        }

        // 置く
        if (cursor != null && cursor.getType() != Material.AIR) {
            if (cursor.getType() != Material.DIAMOND) {
                return;
            }
            // キャンセル済みなので手動で反映する（単純置換）
            int amount = cursor.getAmount();
            if (clicked == null || clicked.getType() == Material.AIR) {
                invSet(player, topSlot, new ItemStack(Material.DIAMOND, amount));
                player.setItemOnCursor(null);
            } else if (clicked.getType() == Material.DIAMOND) {
                int sum = clicked.getAmount() + amount;
                invSet(player, topSlot, new ItemStack(Material.DIAMOND, sum));
                player.setItemOnCursor(null);
            }
            return;
        }

        // 取る
        if (clicked != null && clicked.getType() == Material.DIAMOND) {
            player.setItemOnCursor(clicked.clone());
            invSet(player, topSlot, null);
        }
    }

    private static void invSet(Player player, int topSlot, ItemStack stack) {
        player.getOpenInventory().getTopInventory().setItem(topSlot, stack);
    }
}
