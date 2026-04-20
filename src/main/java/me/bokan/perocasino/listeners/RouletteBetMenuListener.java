package me.bokan.perocasino.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RouletteBetMenuListener implements Listener {

    public static final String GUI_TITLE = "§f\uF808\uE001"; // 左にズラした画像タイトル

    // ベット可能なスロット（4x5）
    private static final Set<Integer> BET_SLOTS = Set.of(
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33,
            38, 39, 40, 41, 42
    );

    private static final int HIDDEN_SLOT = 53; // 右下の隠しマス
    private final Map<UUID, ItemStack[]> savedBets = new HashMap<>();

    public void openBetGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);

        // 以前のベットを復元
        if (savedBets.containsKey(player.getUniqueId())) {
            gui.setContents(savedBets.get(player.getUniqueId()));
        }

        // 隠し要素：見えないバンドルを設置（中身が空なら設置）
        if (gui.getItem(HIDDEN_SLOT) == null) {
            gui.setItem(HIDDEN_SLOT, createInvisibleBundle());
        }

        player.openInventory(gui);
    }

    private ItemStack createInvisibleBundle() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        ItemMeta meta = bundle.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7 "); // 名前を空白にする
            // リソースパックで Bundle の CustomModelData を透明に設定しているならここに追加
            // meta.setCustomModelData(12345);
            bundle.setItemMeta(meta);
        }
        return bundle;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        int slot = event.getRawSlot();
        
        // 上のインベントリ（GUI側）の操作
        if (slot >= 0 && slot < 54) {
            // ベット枠でも隠し枠でもない場所はクリック無効
            if (!BET_SLOTS.contains(slot) && slot != HIDDEN_SLOT) {
                event.setCancelled(true);
                return;
            }

            // ダイヤ以外の配置を禁止（隠し枠は自由にするならここを調整）
            ItemStack cursor = event.getCursor();
            if (slot != HIDDEN_SLOT && cursor != null && cursor.getType() != Material.AIR && cursor.getType() != Material.DIAMOND) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(GUI_TITLE)) {
            savedBets.put(event.getPlayer().getUniqueId(), event.getInventory().getContents());
        }
    }
}