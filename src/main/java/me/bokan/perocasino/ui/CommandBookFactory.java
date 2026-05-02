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
        meta.setDisplayName("§f§lコマンド集");

        meta.setPages(List.of(
                "§0§lコマンド集\n" +
                        "§7一般に使うコマンドと、\n" +
                        "§7管理者向けの設定コマンドです。\n\n" +
                        "§8目次\n" +
                        "§1- 一般\n" +
                        "§1- 管理者（設置）\n" +
                        "§1- ブラックジャック / H&L",
                "§0§l一般\n" +
                        "§7・/casino\n" +
                        "§7  カジノメニュー\n\n" +
                        "§7・/balance（/bal）\n" +
                        "§7  財布・借金の表示\n\n" +
                        "§7・/deposit\n" +
                        "§7  手持ちコインを財布へ\n\n" +
                        "§7・/commandbook（/cb）\n" +
                        "§7  この本を再取得",
                "§0§l管理者（設置）\n" +
                        "§7権限: perocasino.admin\n\n" +
                        "§7・/perocasino roulette set\n" +
                        "§7  見ている砥石をルーレット拠点に\n\n" +
                        "§7・/perocasino quarry set\n" +
                        "§7  採石場（2回で角登録）\n\n" +
                        "§7・/perocasino reload\n" +
                        "§7  config再読込",
                "§0§l管理者（続き）\n" +
                        "§7・/perocasino blackjack dealer set\n" +
                        "§7  近くの村人をBJディーラーに\n\n" +
                        "§7・/perocasino hilo dealer set\n" +
                        "§7  近くの村人をH&Lディーラーに\n\n" +
                        "§7・/perocasino hilo dealer summon\n" +
                        "§7  H&Lディーラーを召喚・登録",
                "§0§lブラックジャック / H&L\n" +
                        "§7・BJはディーラー村人へ話しかけ\n" +
                        "§7  Yes/No → ロビー → 掛け金 → START\n\n" +
                        "§7・H&Lも専用ディーラーへ話しかけ\n" +
                        "§7  ディーラー戦 or 2人対戦\n" +
                        "§7  セット数 5 / 7 / 9 を選択\n\n" +
                        "§7詳細はルールブック参照。"
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
