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
                        "§0一般に使うコマンドと、\n" +
                        "§0管理者向けの設定コマンドです。\n\n" +
                        "§0目次\n" +
                        "§0- 一般\n" +
                        "§0- 管理者（設置）\n" +
                        "§0- ブラックジャック / H&L",
                "§0§l一般\n" +
                        "§0・/casino\n" +
                        "§0  カジノメニュー\n\n" +
                        "§0・/balance（/bal）\n" +
                        "§0  財布・借金の表示\n\n" +
                        "§0・/deposit\n" +
                        "§0  手持ちコインを財布へ\n\n" +
                        "§0・/hilo select <high|low>\n" +
                        "§0  H&L進行中の選択（GUIなし）\n\n" +
                        "§0・/commandbook（/cb）\n" +
                        "§0  この本を再取得",
                "§0§l管理者（設置）\n" +
                        "§0権限: perocasino.admin\n\n" +
                        "§0・/perocasino roulette set\n" +
                        "§0  見ている砥石をルーレット拠点に\n" +
                        "§0  （プレイヤーはその砥石を右クリックでベット）\n\n" +
                        "§0・/perocasino quarry set\n" +
                        "§0  採石場（2回で角登録）\n\n" +
                        "§0・/perocasino reload\n" +
                        "§0  config再読込",
                "§0§l管理者（続き）\n" +
                        "§0・/perocasino blackjack dealer set\n" +
                        "§0  近くの村人をBJディーラーに\n\n" +
                        "§0・/perocasino blackjack dealer summon\n" +
                        "§0  BJディーラーを召喚・登録（固定）\n\n" +
                        "§0・/perocasino hilo dealer set\n" +
                        "§0  近くの村人をH&Lディーラーに\n\n" +
                        "§0・/perocasino hilo dealer summon\n" +
                        "§0  H&Lディーラーを召喚・登録（固定）",
                "§0§lブラックジャック / H&L\n" +
                        "§0・BJはディーラー村人へ話しかけ\n" +
                        "§0  Yes/No → ロビー → 掛け金 → START\n" +
                        "§0  途中で閉じてもディーラー右クリックで復帰\n\n" +
                        "§0・H&Lも専用ディーラーへ話しかけ\n" +
                        "§0  ディーラー戦 or 2人対戦\n" +
                        "§0  セット数 5 / 7 / 9 を選択\n" +
                        "§0  途中で閉じてもディーラー右クリックで復帰\n\n" +
                        "§0詳細はルールブック参照。"
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
