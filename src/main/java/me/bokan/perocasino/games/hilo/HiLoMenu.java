package me.bokan.perocasino.games.hilo;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * ハイアンドロー（骨組み）。
 * 今後: カード生成、連勝倍率、支払い、履歴表示をここに集約する。
 */
public final class HiLoMenu {

    public static final String GUI_TITLE = "§0§lHI-LO (WIP)";

    private HiLoMenu() {}

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);
        inv.setItem(13, pane(Material.PAPER, "§e§l現在のカード（予定）", List.of("§7このGUIは骨組みです。")));
        inv.setItem(11, pane(Material.LIME_CONCRETE, "§a§lHIGH（予定）"));
        inv.setItem(15, pane(Material.RED_CONCRETE, "§c§lLOW（予定）"));
        inv.setItem(22, pane(Material.BARRIER, "§7閉じる"));
        player.openInventory(inv);
    }

    private static ItemStack pane(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack pane(Material mat, String name) {
        return pane(mat, name, null);
    }
}
