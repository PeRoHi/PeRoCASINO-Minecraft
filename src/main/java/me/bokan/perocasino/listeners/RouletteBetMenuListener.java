package me.bokan.perocasino.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RouletteBetMenuListener implements Listener {

    public static final String GUI_TITLE = "§0§lROULETTE - ベット";

    public void openBetGui(Player player) {
        // 画像のような 54マスのチェストUIを作成
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);

        // 例：倍率のアイコンを配置（実際の画像に合わせて色や位置は調整してくれ）
        gui.setItem(11, createMultiplierItem(Material.RED_TERRACOTTA, "§c§l2倍", "§7ここにダイヤを置く"));
        gui.setItem(13, createMultiplierItem(Material.BLUE_TERRACOTTA, "§9§l4倍", "§7ここにダイヤを置く"));
        gui.setItem(15, createMultiplierItem(Material.YELLOW_TERRACOTTA, "§e§l6倍", "§7ここにダイヤを置く"));
        gui.setItem(29, createMultiplierItem(Material.LIME_TERRACOTTA, "§a§l10倍", "§7ここにダイヤを置く"));
        gui.setItem(33, createMultiplierItem(Material.PURPLE_TERRACOTTA, "§5§l20倍", "§7ここにダイヤを置く"));

        // 下部の装飾や情報表示（必要に応じて）
        gui.setItem(49, createMultiplierItem(Material.BARRIER, "§7[閉じる]", ""));

        player.openInventory(gui);
    }

    private ItemStack createMultiplierItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (!lore.isEmpty()) {
            meta.setLore(java.util.List.of(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTitle().equals(GUI_TITLE)) {
            // ここに「ダイヤを置いた時の処理」や「取り消し処理」を追加していく
            // 枠外のクリックや装飾品の移動防止なども必要になる
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.DIAMOND) {
                event.setCancelled(true);
            }
        }
    }
}