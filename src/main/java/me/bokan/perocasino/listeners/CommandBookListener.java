package me.bokan.perocasino.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.List;

/**
 * 初参加プレイヤーに「コマンド集」ルールブックを配布（固定はしない）。
 */
public class CommandBookListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (p.hasPlayedBefore()) return;

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        if (book.getItemMeta() instanceof BookMeta meta) {
            meta.setTitle("PeRoCasino コマンド集");
            meta.setAuthor("PeRoCasino");
            meta.setDisplayName("§e§lコマンド集（初回配布）");
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
                            "  ルーレットの進行を停止/再開",
                    "管理者コマンド（続き）\n\n" +
                            "・/perocasino roulette display set\n" +
                            "  ルーレット表示(ItemDisplay)設置\n\n" +
                            "・/perocasino roulette display remove\n" +
                            "  ルーレット表示を削除\n\n" +
                            "・/perocasino quarry set\n" +
                            "  採石場範囲を登録（2回）"
            ));
            book.setItemMeta(meta);
        }

        p.getInventory().addItem(book);
    }
}

