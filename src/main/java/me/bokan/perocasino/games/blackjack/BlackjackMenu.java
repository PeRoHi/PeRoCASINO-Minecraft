package me.bokan.perocasino.games.blackjack;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * ブラックジャック（骨組み）。
 * 今後: デッキ、ヒット/スタンド、ディーラーAI、支払いをここに集約する。
 */
public final class BlackjackMenu {

    public static final String GUI_TITLE = "§0§lBLACKJACK (WIP)";

    private BlackjackMenu() {}

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);
        inv.setItem(11, pane(Material.LIME_STAINED_GLASS_PANE, "§a§lHIT（予定）"));
        inv.setItem(15, pane(Material.RED_STAINED_GLASS_PANE, "§c§lSTAND（予定）"));
        inv.setItem(13, pane(Material.PAPER, "§e§lルール（予定）", List.of(
                "§7このGUIは骨組みです。",
                "§7次の実装でカード表示・勝敗処理を追加します。"
        )));
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
