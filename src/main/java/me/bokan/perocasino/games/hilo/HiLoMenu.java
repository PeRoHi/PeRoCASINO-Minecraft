package me.bokan.perocasino.games.hilo;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 旧カジノメニューからの入口用。
 * 実ゲームはH&Lディーラー村人から開始するため、ここでは案内だけ表示する。
 */
public final class HiLoMenu {

    public static final String GUI_TITLE = "§0§lHI-LO (WIP)";

    private HiLoMenu() {}

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);
        inv.setItem(13, pane(Material.PAPER, "§e§lHigh and Low", List.of(
                "§7H&Lディーラー村人に話しかけて",
                "§7ディーラー戦 / 2人対戦を選択します。",
                "§f",
                "§7管理者:",
                "§e/perocasino hilo dealer set",
                "§e/perocasino hilo dealer summon"
        )));
        inv.setItem(11, pane(Material.LIME_CONCRETE, "§a§lHIGH"));
        inv.setItem(15, pane(Material.RED_CONCRETE, "§c§lLOW"));
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
