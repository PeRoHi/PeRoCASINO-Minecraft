package me.bokan.perocasino.ui;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class CommandBookFactory {

    private CommandBookFactory() {}

    private static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "command_book");
    }

    public static ItemStack create() {
        return create(null);
    }

    public static ItemStack create(Plugin plugin) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        if (book.getItemMeta() instanceof BookMeta meta) {
            meta.setTitle("PeRoCasino コマンド集");
            meta.setAuthor("PeRoCasino");
            meta.setDisplayName("§e§lコマンド集");
            meta.setPages(List.of(
                    "PeRoCasino コマンド集\n\n" +
                            "基本:\n" +
                            "/casino\n" +
                            "/balance\n" +
                            "/deposit\n\n" +
                            "管理者:\n" +
                            "/perocasino ...",
                    "一般コマンド\n\n" +
                            "・/casino\n" +
                            "  カジノメニューを開く\n\n" +
                            "・/balance\n" +
                            "  財布残高/借金を表示\n\n" +
                            "・/deposit\n" +
                            "  手持ちのビーストコイン(ダイヤ)を財布へ",
                    "管理者コマンド\n\n" +
                            "・/perocasino roulette set\n" +
                            "  砥石をルーレット拠点に登録\n\n" +
                            "・/perocasino roulette remove\n" +
                            "  ルーレット拠点を削除\n\n" +
                            "・/perocasino roulette stop/start\n" +
                            "  ルーレットの進行を停止/再開\n\n" +
                            "・/perocasino roulette board set\n" +
                            "  砥石ベット盤(左端)を登録",
                    "管理者コマンド（続き）\n\n" +
                            "・/perocasino roulette display set\n" +
                            "  ルーレット表示(ItemDisplay)設置\n\n" +
                            "・/perocasino roulette display remove\n" +
                            "  ルーレット表示を削除\n\n" +
                            "・/perocasino quarry set\n" +
                            "  採石場範囲を登録（2回）"
            ));
            if (plugin != null) {
                meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
            }
            book.setItemMeta(meta);
        }
        return book;
    }

    public static boolean isCommandBook(ItemStack item, Plugin plugin) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK) return false;
        if (!item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    public static boolean giveIfMissing(Player player, Plugin plugin) {
        for (ItemStack it : player.getInventory().getContents()) {
            if (isCommandBook(it, plugin)) return false;
        }
        player.getInventory().addItem(create(plugin));
        return true;
    }
}

