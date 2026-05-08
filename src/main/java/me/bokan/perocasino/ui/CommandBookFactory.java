package me.bokan.perocasino.ui;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class CommandBookFactory {

    private CommandBookFactory() {}

    public static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "command_book");
    }

    public static ItemStack create(Plugin plugin) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta raw = book.getItemMeta();
        if (!(raw instanceof BookMeta meta)) return book;

        meta.setTitle("PeRoCasino コマンド集");
        meta.setAuthor("PeRoCasino");
        meta.setDisplayName("§0§lコマンド集");

        meta.setPages(List.of(
                "§0§lコマンド集\n" +
                        "§0一般に使うコマンドと、管理者向けの\n" +
                        "§0設定コマンドをまとめています。\n\n" +
                        "§0目次\n" +
                        "§0- 一般\n" +
                        "§0- 管理者（設置）",
                "§0§l一般\n" +
                        "§0・/casino\n" +
                        "§0  カジノメニュー\n\n" +
                        "§0・/casino <プレイヤー名|セレクター>\n" +
                        "§0  対象プレイヤーにカジノメニューを開く\n\n" +
                        "§0・/balance（/bal）\n" +
                        "§0  財布・借金の表示\n\n" +
                        "§0・/deposit\n" +
                        "§0  手持ちコインを財布へ\n\n" +
                        "§0・/hilo select <high|low>\n" +
                        "§0  進行中のH&Lで選択\n" +
                        "§0  （/hilo high|low|h|l|hi|lo も可）\n\n" +
                        "§0・/commandbook（/cb）\n" +
                        "§0  この本を再取得",
                "§0§l管理者（設置）\n" +
                        "§0権限: perocasino.admin\n\n" +
                        "§0・/perocasino（/pc）\n" +
                        "§0  サブコマンド一覧を表示\n\n" +
                        "§0・/perocasino roulette set\n" +
                        "§0  見ている砥石をルーレット拠点に\n\n" +
                        "§0・/perocasino blackjack dealer set|summon\n" +
                        "§0  BJディーラーを設定/召喚\n\n" +
                        "§0・/perocasino hilo dealer set|summon\n" +
                        "§0  H&Lディーラーを設定/召喚\n\n" +
                        "§0・/perocasino quarry set\n" +
                        "§0  採石場の角2点を登録（2回実行）\n\n" +
                        "§0・/perocasino slot create|remove|list\n" +
                        "§0  設置スロット（TextDisplay）を登録/削除/一覧\n\n" +
                        "§0・/perocasino reload\n" +
                        "§0  config再読込"
        ));

        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        book.setItemMeta(meta);
        return book;
    }

    public static boolean isCommandBook(ItemStack item, Plugin plugin) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    public static boolean giveIfMissing(org.bukkit.entity.Player player, Plugin plugin) {
        for (ItemStack it : player.getInventory().getContents()) {
            if (isCommandBook(it, plugin)) return false;
        }
        player.getInventory().addItem(create(plugin));
        return true;
    }
}

