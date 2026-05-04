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
        meta.setDisplayName("§f§lルールブック（固定）");

        meta.setPages(List.of(
                "§0§lPeRoCasino ルール\n" +
                        "§0この本は左下に固定されています。\n" +
                        "§0（移動・ドロップ不可）\n\n" +
                        "§0目次\n" +
                        "§1- 財布\n" +
                        "§1- スロット\n" +
                        "§1- ルーレット\n" +
                        "§1- 採石場\n" +
                        "§1- ブラックジャック\n" +
                        "§1- ハイアンドロー\n" +
                        "§1- その他",
                "§0§l財布（Wallet）\n" +
                        "§0右側に固定アイテムが入ります。\n\n" +
                        "§0・ダイヤ引き出し口\n" +
                        "§0左クリック: カーソルへ最大64\n" +
                        "§0シフト左クリック: インベントリへ最大64\n\n" +
                        "§0・専用バンドル\n" +
                        "§0ダイヤを入れると財布に収納\n" +
                        "§0シフトクリックで全ダイヤ回収",
                "§0§lスロット（Slot）\n" +
                        "§0鉱石絵柄が順繰りに回転します。\n\n" +
                        "§0・開始\n" +
                        "§0スピンボタンを右クリック\n" +
                        "§0（GUIのベット枠にダイヤを置く）\n\n" +
                        "§f・停止\n" +
                        "§0各停止ボタンは別々。\n" +
                        "§0押すとランダムtick後に停止。\n\n" +
                        "§0・精算\n" +
                        "§0結果とベットから払い戻し（財布へ）",
                "§0§lルーレット（Roulette）\n" +
                        "§0拠点の砥石を右クリックでベット。\n\n" +
                        "§0・タイマー\n" +
                        "§0半径40でBossBar表示\n" +
                        "§0ベット20秒 → 抽選 → クール5秒\n" +
                        "§0を繰り返します。\n\n" +
                        "§f・注意\n" +
                        "§0抽選中はGUIを閉じられません。",
                "§0§l採石場（Quarry）\n" +
                        "§0採石場範囲内のダイヤ鉱石は\n" +
                        "§0掘ると丸石に変わります。\n\n" +
                        "§0・復活\n" +
                        "§0約5分（6000tick）で\n" +
                        "§0ダイヤ鉱石へ戻ります。\n\n" +
                        "§0範囲設定: /perocasino quarry set\n" +
                        "§0（2回で角を登録）",
                "§0§lブラックジャック\n" +
                        "§0ディーラー村人に右クリックで参加。\n\n" +
                        "§0・ディーラー\n" +
                        "§0管理者: /perocasino blackjack dealer set\n" +
                        "§0（近くの村人を登録）\n" +
                        "§0または名前に Blackjack / ブラック\n" +
                        "§0ジャック / ディーラー を含む村人\n\n" +
                        "§0・流れ\n" +
                        "§0Yes/No → ロビーで他プレイヤー待ち\n" +
                        "§0掛け金（手持ちコイン）を選び\n" +
                        "§0最初にYesを押した人がSTART",
                "§0§lブラックジャック（続）\n" +
                        "§0カードは紙で手元に配られます。\n" +
                        "§0移動は操作GUIから。\n\n" +
                        "§0・操作\n" +
                        "§0HIT 引く / STAND 止める\n" +
                        "§0DOUBLE 掛け2倍+1枚\n" +
                        "§0SWITCH Aの1/11切替\n" +
                        "§0SURRENDER 半額返却\n\n" +
                        "§0・配当（目安）\n" +
                        "§0勝ち2倍 / 21で3倍\n" +
                        "§0ダブルで21勝ち5倍\n" +
                        "§0引分は掛け返却",
                "§0§lハイアンドロー\n" +
                        "§0H&Lディーラー村人に右クリック。\n\n" +
                        "§0・ディーラー\n" +
                        "§0/perocasino hilo dealer set\n" +
                        "§0（近くの村人を登録）\n" +
                        "§0または summon で召喚・登録\n" +
                        "§0名前に H&L / ハイロー 等\n\n" +
                        "§0・流れ\n" +
                        "§0モード（ディーラー戦 or 2人）\n" +
                        "§0セット数 5 / 7 / 9\n" +
                        "§0ロビーで待機→掛け金→START",
                "§0§lハイアンドロー（続）\n" +
                        "§0親は表カード（頭上）、子は裏（？）。\n" +
                        "§0子が High / Low を選択。\n" +
                        "§0当たりで1pt。\n\n" +
                        "§0・精算（目安）\n" +
                        "§0最終pt差で配当（切捨）\n" +
                        "§0勝: bet×(差+1)×1.2 等\n" +
                        "§0同点は返却、負けは没収\n" +
                        "§0差2以上は追加徴収（財布）",
                "§0§lその他\n" +
                        "§0ゲームは随時追加・調整されます。\n\n" +
                        "§0コマンド一覧は\n" +
                        "§0コマンド集ブックを参照。"
        ));

        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        book.setItemMeta(meta);
        return book;
    }
}

