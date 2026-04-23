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
                        "§7この本は左下に固定されています。\n" +
                        "§7（移動・ドロップ不可）\n\n" +
                        "§8目次\n" +
                        "§1- 財布\n" +
                        "§1- スロット\n" +
                        "§1- ルーレット\n" +
                        "§1- 採石場\n" +
                        "§1- 今後のゲーム",
                "§0§l財布（Wallet）\n" +
                        "§7右側に固定アイテムが入ります。\n\n" +
                        "§b・ダイヤ引き出し口\n" +
                        "§7左クリック: カーソルへ最大64\n" +
                        "§7シフト左クリック: インベントリへ最大64\n\n" +
                        "§6・専用バンドル\n" +
                        "§7ダイヤを入れると財布に収納\n" +
                        "§7シフトクリックで全ダイヤ回収",
                "§0§lスロット（Slot）\n" +
                        "§7鉱石絵柄が順繰りに回転します。\n\n" +
                        "§a・開始\n" +
                        "§7スピンボタンを右クリック\n" +
                        "§7（GUIのベット枠にダイヤを置く）\n\n" +
                        "§e・停止\n" +
                        "§7各停止ボタンは別々。\n" +
                        "§7押すとランダムtick後に停止。\n\n" +
                        "§b・精算\n" +
                        "§7結果とベットから払い戻し（財布へ）",
                "§0§lルーレット（Roulette）\n" +
                        "§7拠点の砥石を右クリックでベット。\n\n" +
                        "§a・タイマー\n" +
                        "§7半径40でBossBar表示\n" +
                        "§7ベット20秒 → 抽選 → クール5秒\n" +
                        "§7を繰り返します。\n\n" +
                        "§e・注意\n" +
                        "§7抽選中はGUIを閉じられません。",
                "§0§l採石場（Quarry）\n" +
                        "§7採石場範囲内のダイヤ鉱石は\n" +
                        "§7掘ると丸石に変わります。\n\n" +
                        "§a・復活\n" +
                        "§7約5分（6000tick）で\n" +
                        "§7ダイヤ鉱石へ戻ります。\n\n" +
                        "§7範囲設定: /perocasino quarry set\n" +
                        "§7（2回で角を登録）",
                "§0§l今後のゲーム（WIP）\n" +
                        "§7ブラックジャック / ハイアンドローは\n" +
                        "§7現在GUIの骨組みのみです。\n\n" +
                        "§7順次、勝敗処理・倍率・精算を\n" +
                        "§7実装していきます。"
        ));

        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        book.setItemMeta(meta);
        return book;
    }
}

