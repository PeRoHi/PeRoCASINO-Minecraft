package me.bokan.perocasino.games.blackjack;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 旧カジノメニューからの入口用。
 * 実ゲームは村人ディーラーから開始するため、ここでは案内だけ表示する。
 */
public final class BlackjackMenu {

    public static final String GUI_TITLE = "§0§lBLACKJACK (WIP)";

    private BlackjackMenu() {}

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);
        inv.setItem(13, pane(Material.PAPER, "§e§lブラックジャック", List.of(
                "§7ブラックジャックはディーラー村人に",
                "§7話しかけて開始します。",
                "§f",
                "§7ディーラー設定:",
                "§e/perocasino blackjack dealer set",
                "§7または名前に Blackjack / ブラックジャック / ディーラー",
                "§7を含む村人を右クリック"
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
