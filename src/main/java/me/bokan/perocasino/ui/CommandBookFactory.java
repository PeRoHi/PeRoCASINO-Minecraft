package me.bokan.perocasino.ui;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
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
                        "§0PeRoCasino のコマンド一覧です。\n\n" +
                        "§0§l本の入手\n" +
                        "§0・/commandbook（/cb）\n" +
                        "§0  未所持なら1冊配布\n" +
                        "§0・/commandbook refresh\n" +
                        "§0  持っている本を最新版に差替\n\n" +
                        "§0目次\n" +
                        "§0- 一般（2〜3頁）\n" +
                        "§0- コマンド杖（4頁）\n" +
                        "§0- 管理者（5〜7頁）",
                "§0§l一般（1/2）\n" +
                        "§0・/casino\n" +
                        "§0  カジノメニュー\n\n" +
                        "§0・/casino <名前|@p等>\n" +
                        "§0  対象にメニューを開く\n\n" +
                        "§0・/balance（/bal）\n" +
                        "§0  財布・借金を表示\n\n" +
                        "§0・/deposit\n" +
                        "§0  手持ちダイヤを財布へ",
                "§0§l一般（2/2）\n" +
                        "§0・/hilo select <high|low>\n" +
                        "§0  進行中H&Lで選択\n" +
                        "§0  （/hilo high|low 等も可）\n\n" +
                        "§0・/chinchiro roll\n" +
                        "§0  サイコロ3個（表示領域要）\n" +
                        "§0  卓は村人ディーラーからも可\n\n" +
                        "§0・/commandbook（/cb）\n" +
                        "§0  この本を入手・更新",
                "§0§lコマンド杖\n" +
                        "§0人参付きの棒に付けた\n" +
                        "§0表示名でコマンドが変わる。\n" +
                        "§0右クリックで実行。\n\n" +
                        "§0例（configのwands）\n" +
                        "§0・BJディーラー設置\n" +
                        "§0・ルーレット設置\n" +
                        "§0・サイコロ振る\n" +
                        "§0・tp-100-64-200（座標TP）\n\n" +
                        "§0有効化: command-wand.enabled\n" +
                        "§0権限: perocasino.commandwand",
                "§0§l管理者（1/3）\n" +
                        "§0権限: perocasino.admin\n" +
                        "§0別名: /pc\n\n" +
                        "§0§lルーレット\n" +
                        "§0・roulette set\n" +
                        "§0  砥石を拠点に（6m以内）\n" +
                        "§0・roulette remove\n" +
                        "§0  拠点登録を削除\n" +
                        "§0・roulette start / stop\n" +
                        "§0  進行の再開・一時停止\n" +
                        "§0・roulette board set\n" +
                        "§0  砥石ベット盤・左端砥石",
                "§0§l管理者（2/3）\n" +
                        "§0§lルーレット（続）\n" +
                        "§0・roulette display set\n" +
                        "§0  見ている面に盤面\n" +
                        "§0  （ItemDisplay）\n" +
                        "§0・roulette display remove\n" +
                        "§0  盤面を削除\n\n" +
                        "§0§lブラックジャック\n" +
                        "§0・blackjack dealer set\n" +
                        "§0・blackjack dealer summon\n\n" +
                        "§0§lハイアンドロー\n" +
                        "§0・hilo dealer set\n" +
                        "§0・hilo dealer summon",
                "§0§l管理者（3/3）\n" +
                        "§0§lチンチロ\n" +
                        "§0・chinchiro dealer set|summon\n" +
                        "§0  卓ディーラー村人\n" +
                        "§0・chinchiro region set\n" +
                        "§0  サイコロ範囲（角2回）\n\n" +
                        "§0§lスロット（設置型）\n" +
                        "§0・slot create <id>\n" +
                        "§0・slot remove <id>\n" +
                        "§0・slot list\n" +
                        "§0・slot dealer set|summon\n\n" +
                        "§0・quarry set（角2回）\n" +
                        "§0・wandchest\n" +
                        "§0  見ている位置に\n" +
                        "§0  コマンド杖チェスト設置\n" +
                        "§0・reload\n" +
                        "§0  config再読込"
        ));

        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        book.setItemMeta(meta);
        return book;
    }

    /** インベントリ内のコマンド集を最新版に差し替え。無ければ1冊追加。差し替えた冊数を返す。 */
    public static int refresh(Player player, Plugin plugin) {
        int replaced = 0;
        ItemStack fresh = create(plugin);
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack it = player.getInventory().getItem(slot);
            if (!isCommandBook(it, plugin)) {
                continue;
            }
            player.getInventory().setItem(slot, fresh.clone());
            replaced++;
        }
        if (replaced == 0) {
            player.getInventory().addItem(fresh);
            return 1;
        }
        return replaced;
    }

    public static boolean isCommandBook(ItemStack item, Plugin plugin) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    public static boolean giveIfMissing(Player player, Plugin plugin) {
        for (ItemStack it : player.getInventory().getContents()) {
            if (isCommandBook(it, plugin)) return false;
        }
        player.getInventory().addItem(create(plugin));
        return true;
    }
}
