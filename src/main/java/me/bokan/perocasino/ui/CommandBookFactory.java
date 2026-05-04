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
                                    "§8一般に使うコマンドと、\n" +
                                    "§8管理者向けの設定コマンドです。\n\n" +
                        "§8目次\n" +
                        "§1- 一般\n" +
                        "§1- 管理者（設置）\n" +
                        "§1- ブラックジャック / H&L",
                "§0§l一般\n" +
                                    "§8・§f/casino\n" +
                                    "§8  カジノメニュー\n\n" +
                                    "§8・§f/balance§8（§f/bal§8）\n" +
                                    "§8  財布・借金の表示\n\n" +
                                    "§8・§f/deposit\n" +
                                    "§8  手持ちコインを財布へ\n\n" +
                                    "§8・§f/hilo select <high|low>\n" +
                                    "§8  H&L進行中の選択（GUIなし）\n\n" +
                                    "§8・§f/commandbook§8（§f/cb§8）\n" +
                                    "§8  この本を再取得",
                "§0§l管理者（設置）\n" +
                        "§7権限: perocasino.admin\n\n" +
                                    "§8・§f/perocasino roulette set\n" +
                                    "§8  見ている砥石をルーレット拠点に\n" +
                                    "§8  （プレイヤーはその砥石を右クリックでベット）\n\n" +
                                    "§8・§f/perocasino quarry set\n" +
                                    "§8  採石場（2回で角登録）\n\n" +
                                    "§8・§f/perocasino reload\n" +
                                    "§8  config再読込",
                "§0§l管理者（続き）\n" +
                                    "§8・§f/perocasino blackjack dealer set\n" +
                                    "§8  近くの村人をBJディーラーに\n\n" +
                                    "§8・§f/perocasino blackjack dealer summon\n" +
                                    "§8  BJディーラーを召喚・登録（固定）\n\n" +
                                    "§8・§f/perocasino hilo dealer set\n" +
                                    "§8  近くの村人をH&Lディーラーに\n\n" +
                                    "§8・§f/perocasino hilo dealer summon\n" +
                                    "§8  H&Lディーラーを召喚・登録（固定）",
                "§0§lブラックジャック / H&L\n" +
                                    "§8・BJはディーラー村人へ話しかけ\n" +
                                    "§8  Yes/No → ロビー → 掛け金 → START\n" +
                                    "§8  途中で閉じてもディーラー右クリックで復帰\n\n" +
                                    "§8・H&Lも専用ディーラーへ話しかけ\n" +
                                    "§8  ディーラー戦 or 2人対戦\n" +
                                    "§8  セット数 5 / 7 / 9 を選択\n" +
                                    "§8  途中で閉じてもディーラー右クリックで復帰\n\n" +
                                    "§8詳細はルールブック参照。"
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
