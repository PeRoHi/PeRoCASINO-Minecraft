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
                        "§1- ブラックジャック\n" +
                        "§1- その他",
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
                "§0§lブラックジャック\n" +
                        "§7ディーラー村人に右クリックで参加。\n\n" +
                        "§a・ディーラー\n" +
                        "§7管理者: /perocasino blackjack dealer set\n" +
                        "§7（近くの村人を登録）\n" +
                        "§7または名前に Blackjack / ブラック\n" +
                        "§7ジャック / ディーラー を含む村人\n\n" +
                        "§a・流れ\n" +
                        "§7Yes/No → ロビーで他プレイヤー待ち\n" +
                        "§7掛け金（手持ちコイン）を選び\n" +
                        "§7最初にYesを押した人がSTART",
                "§0§lブラックジャック（続）\n" +
                        "§7カードは紙で手元に配られます。\n" +
                        "§7移動は操作GUIから。\n\n" +
                        "§a・操作\n" +
                        "§7HIT 引く / STAND 止める\n" +
                        "§7DOUBLE 掛け2倍+1枚\n" +
                        "§7SWITCH Aの1/11切替\n" +
                        "§7SURRENDER 半額返却\n\n" +
                        "§a・配当（目安）\n" +
                        "§7勝ち2倍 / 21で3倍\n" +
                        "§7ダブルで21勝ち5倍\n" +
                        "§7引分は掛け返却",
                "§0§lその他\n" +
                        "§7ハイアンドロー等は\n" +
                        "§7準備中の場合があります。\n\n" +
                        "§7コマンド一覧は\n" +
                        "§7コマンド集ブックを参照。"
        ));

        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        book.setItemMeta(meta);
        return book;
    }
}

