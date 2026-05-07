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
        meta.setDisplayName("§0§lルールブック（固定）");

        meta.setPages(List.of(
                "§0§lPeRoCasino 概要\n" +
                        "§0この本は左下に固定されています。\n" +
                        "§0（移動・ドロップ不可）\n\n" +
                        "§0PeRoCasino は拠点内で遊べる\n" +
                        "§0カジノ系ミニゲームと、\n" +
                        "§0経済（財布）を扱うプラグインです。\n\n" +
                        "§0参加や操作は\n" +
                        "§0各ゲームのGUI表示に従ってください。",
                "§0§lルーレット\n" +
                        "§0拠点の砥石から参加します。\n\n" +
                        "§0・進行\n" +
                        "§0ベット受付 → 抽選 → クールダウン\n" +
                        "§0を自動で繰り返します。\n\n" +
                        "§0・BossBar\n" +
                        "§0拠点付近では残り時間が表示されます。",
                "§0§lルーレット（精算）\n" +
                        "§0抽選は停止角度(0〜359°)で決まり、\n" +
                        "§0角度に対応した倍率が結果になります。\n\n" +
                        "§0・払戻\n" +
                        "§0合計ベット×倍率 が財布に入ります。\n\n" +
                        "§0・注意\n" +
                        "§0抽選中はベットGUIを閉じられません。",
                "§0§lHigh & Low\n" +
                        "§0ディーラー村人に話しかけて開始します。\n\n" +
                        "§0・モード\n" +
                        "§0ディーラー戦 / 2人対戦\n\n" +
                        "§0・セット数\n" +
                        "§05 / 7 / 9 から選びます。\n\n" +
                        "§0・勝負\n" +
                        "§0親の表カードより子の伏せカードが\n" +
                        "§0高いか低いかを選びます。\n" +
                        "§0同じ数字はDRAW(得点なし)です。",
                "§0§lHigh & Low（精算）\n" +
                        "§0当たりで1pt、最終pt差で勝敗が決まります。\n\n" +
                        "§0・掛け金\n" +
                        "§0財布ではなく手持ちのコインから預けます。\n\n" +
                        "§0・配当（目安）\n" +
                        "§0勝者は差に応じて配当を受け取ります。\n" +
                        "§0負け側は掛け金没収、\n" +
                        "§0差が大きい場合は追加徴収があります。",
                "§0§lブラックジャック\n" +
                        "§0ディーラー村人に話しかけて参加します。\n\n" +
                        "§0・ロビー\n" +
                        "§0参加と掛け金を揃え、ホストが開始します。\n\n" +
                        "§0・掛け金\n" +
                        "§0財布ではなく手持ちのコインから預けます。\n\n" +
                        "§0・目標\n" +
                        "§0手札を21に近づけ、\n" +
                        "§0超えたらバーストで負けです。",
                "§0§lブラックジャック（操作）\n" +
                        "§0操作はゲーム内GUIから行います。\n\n" +
                        "§0・HIT\n" +
                        "§01枚引く\n\n" +
                        "§0・STAND\n" +
                        "§0止める\n\n" +
                        "§0・DOUBLE DOWN\n" +
                        "§0掛け金を倍にして1枚引き、即スタンド\n\n" +
                        "§0・SWITCH\n" +
                        "§0Aの扱い(11優先/1固定)を切替\n\n" +
                        "§0・SURRENDER\n" +
                        "§0掛け金の半分を戻して降りる",
                "§0§l採石場（Quarry）\n" +
                        "§0採石場はダイヤ鉱石の\n" +
                        "§0リスポーンを管理するエリアです。\n\n" +
                        "§0範囲内のダイヤ鉱石/深層ダイヤ鉱石は、\n" +
                        "§0掘られると丸石に置き換わります。\n\n" +
                        "§0置き換わった丸石は一定時間後に\n" +
                        "§0ダイヤ鉱石へ戻ります。",
                "§0§l採石場（注意）\n" +
                        "§0採石場は指定範囲でのみ有効です。\n" +
                        "§0範囲外の鉱石には影響しません。\n\n" +
                        "§0リスポーン時間などの詳細は\n" +
                        "§0サーバー設定に依存します。\n\n" +
                        "§0設定手順は\n" +
                        "§0コマンド集ブックを参照してください。"
        ));

        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        book.setItemMeta(meta);
        return book;
    }
}

