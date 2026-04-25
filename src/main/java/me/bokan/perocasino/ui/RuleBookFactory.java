package me.bokan.perocasino.ui;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class RuleBookFactory {

    private RuleBookFactory() {}

    public static final int FIXED_SLOT = 0; // ホットバー左端（見た目の左下）

    public static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "rulebook_item");
    }

    public static ItemStack create(Plugin plugin) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta raw = book.getItemMeta();
        if (!(raw instanceof BookMeta meta)) return book;

        meta.setTitle("PeRoCasino ルールブック");
        meta.setAuthor("PeRoCasino");
        meta.setDisplayName("§e§lルールブック（固定）");

        meta.setPages(List.of(
                "§0§lPeRoCasino ルール\n" +
                        "§0この本は左下に固定です。\n" +
                        "§8（移動・ドロップ不可）\n\n" +
                        "§0§l目次\n" +
                        "§0- 財布\n" +
                        "§0- スロット\n" +
                        "§0- ルーレット\n" +
                        "§0- 採石場\n" +
                        "§0- WIP",
                "§0§l財布（Wallet）\n" +
                        "§0右側に固定アイテムがあります。\n\n" +
                        "§1・ダイヤ引き出し口\n" +
                        "§0左クリック: カーソルへ最大64\n" +
                        "§0Shift+左クリック: インベントリへ最大64\n\n" +
                        "§6・専用バンドル\n" +
                        "§0ダイヤを入れると財布に収納\n" +
                        "§0Shiftクリックで全ダイヤ回収",
                "§0§lスロット（Slot）\n" +
                        "§0鉱石絵柄が順に回転します。\n\n" +
                        "§2・開始\n" +
                        "§0スピンボタン右クリック\n" +
                        "§8（GUIのベット枠にダイヤ）\n\n" +
                        "§e・停止\n" +
                        "§0停止ボタンは3つ別々。\n" +
                        "§0押すとランダムtick後に停止。\n\n" +
                        "§b・精算\n" +
                        "§0結果とベットから払い戻し（財布）",
                "§0§lルーレット（Roulette）\n" +
                        "§0砥石を右クリックでベット。\n\n" +
                        "§2・タイマー\n" +
                        "§0半径40でBossBar表示\n" +
                        "§0ベット20秒 → 回転 → 5秒クール\n\n" +
                        "§e・回転表示\n" +
                        "§0管理者が壁に表示を設置すると\n" +
                        "§0SPINNING中に自動で回転します。\n" +
                        "§8/pc roulette display set\n" +
                        "§8/pc roulette display remove",
                "§0§l採石場（Quarry）\n" +
                        "§0採石場内のダイヤ鉱石は\n" +
                        "§0掘ると丸石に変わります。\n\n" +
                        "§2・復活\n" +
                        "§0約5分（6000tick）で戻る\n\n" +
                        "§8範囲設定: /pc quarry set（2回）",
                "§0§lWIP\n" +
                        "§0ブラックジャック / ハイローは\n" +
                        "§0いまはGUI骨組みのみです。\n\n" +
                        "§8順次、勝敗・倍率・精算を追加"
        ));

        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        book.setItemMeta(meta);
        return book;
    }
}

