package me.bokan.perocasino.listeners;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * カスタムGUIでのシフト／数字キー／ダブルクリック収集／ドラッグによる複製・持ち出しを止める。
 */
public final class GuiSafety {

    private GuiSafety() {}

    public static boolean isTopInventory(InventoryClickEvent event) {
        return event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());
    }

    public static void cancelOutsideClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
        }
    }

    /**
     * 下側インベントリからの、上側への移動になり得る操作をキャンセルする。
     * @return キャンセルした場合 true（呼び出し側はそれ以上処理しない）
     */
    public static boolean cancelUnsafeBottomMoves(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return true;
        }
        if (isTopInventory(event)) {
            return false;
        }
        ClickType type = event.getClick();
        if (type == ClickType.SHIFT_LEFT
                || type == ClickType.SHIFT_RIGHT
                || type == ClickType.DOUBLE_CLICK
                || type == ClickType.NUMBER_KEY
                || type == ClickType.SWAP_OFFHAND
                || type == ClickType.UNKNOWN) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    /** オフハンド入れ替えなど、カーソル経由でない移動を止める。 */
    public static boolean cancelExoticClicks(InventoryClickEvent event) {
        ClickType type = event.getClick();
        if (type == ClickType.SWAP_OFFHAND || type == ClickType.UNKNOWN) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    /** 上側スロットを含むドラッグはすべて拒否。 */
    public static void cancelDragIfTopTouched(InventoryDragEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw >= 0 && raw < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
