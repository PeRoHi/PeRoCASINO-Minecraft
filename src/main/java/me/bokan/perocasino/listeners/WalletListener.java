package me.bokan.perocasino.listeners;

import me.bokan.perocasino.economy.EconomyManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;

/**
 * プレイヤーの財布システムを管理するリスナー。
 *
 * スロット 8（ホットバー右端）: ダイヤ引き出し口 [NETHER_STAR]
 *   - 左クリック          : 財布から最大64ダイヤをカーソルへ
 *   - シフト+左クリック   : 財布から最大64ダイヤをインベントリへ直接
 *
 * スロット 35（スロット8の真上）: 専用バンドル [BUNDLE]
 *   - ダイヤをドラッグ&ドロップ : 財布に収納
 *   - シフト+左クリック         : インベントリ内の全ダイヤを財布に収納
 *
 * インベントリ内のダイヤにシフト+左クリック → 直接財布に収納（移動をキャンセル）
 */
public class WalletListener implements Listener {

    private static final int WITHDRAW_SLOT = 8;
    private static final int BUNDLE_SLOT   = 35;

    private final EconomyManager economyManager;
    private final NamespacedKey walletKey;

    public WalletListener(EconomyManager economyManager, Plugin plugin) {
        this.economyManager = economyManager;
        this.walletKey = new NamespacedKey(plugin, "wallet_item");
    }

    /** プレイヤーのスロット 8・35 に財布アイテムを配置する。 */
    public void setupWalletItems(Player player) {
        player.getInventory().setItem(WITHDRAW_SLOT, createWithdrawItem());
        player.getInventory().setItem(BUNDLE_SLOT,   createBundleItem());
    }

    // -----------------------------------------------------------------------
    // イベント処理
    // -----------------------------------------------------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        setupWalletItems(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // 数字キー HOTBAR_SWAP でスロット8へのアイテム持ち込みをブロック
        if (event.getAction() == InventoryAction.HOTBAR_SWAP
                && event.getHotbarButton() == WITHDRAW_SLOT) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getClickedInventory() instanceof PlayerInventory)) return;
        int slot = event.getSlot();

        // ---- クリエイティブモードは財布スロットを完全ブロック ----
        if (player.getGameMode() == GameMode.CREATIVE
                && (slot == WITHDRAW_SLOT || slot == BUNDLE_SLOT)) {
            event.setCancelled(true);
            return;
        }

        // ---- 財布スロットの処理 ----
        if (slot == WITHDRAW_SLOT) {
            event.setCancelled(true);
            handleWithdraw(player, event);
            return;
        }
        if (slot == BUNDLE_SLOT) {
            event.setCancelled(true);
            handleBundle(player, event);
            return;
        }

        // ---- インベントリ内のダイヤへのシフトクリック → 財布に収納 ----
        if (event.isShiftClick()
                && event.getCurrentItem() != null
                && event.getCurrentItem().getType() == Material.DIAMOND) {
            event.setCancelled(true);
            int amount = event.getCurrentItem().getAmount();
            economyManager.addWalletBalance(player.getUniqueId(), amount);
            player.getInventory().setItem(slot, null);
            player.sendMessage("§a" + amount + " ダイヤを財布に収納しました。財布: "
                    + economyManager.getWalletBalance(player.getUniqueId()));
        }
    }

    /** スロット35へのドラッグ&ドロップを預け入れとして処理する。 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;

        // ドラッグ先にスロット35（PlayerInventory内）が含まれているか確認
        Map<Integer, ItemStack> newItems = event.getNewItems();
        if (!newItems.containsKey(BUNDLE_SLOT)) return;
        // スロット35が PlayerInventory に属するかチェック（raw slot は view により異なる）
        // getRawSlots() のいずれかが bundle slot に対応するかを確認
        boolean targetsBundleSlot = event.getRawSlots().stream().anyMatch(rawSlot -> {
            try {
                return event.getView().convertSlot(rawSlot) == BUNDLE_SLOT
                        && event.getView().getInventory(rawSlot) instanceof PlayerInventory;
            } catch (Exception e) {
                return false;
            }
        });
        if (!targetsBundleSlot) return;

        if (event.getOldCursor() == null
                || event.getOldCursor().getType() != Material.DIAMOND) return;

        event.setCancelled(true);

        // ドラッグされたダイヤの合計を計算
        int total = newItems.values().stream()
                .filter(i -> i != null && i.getType() == Material.DIAMOND)
                .mapToInt(ItemStack::getAmount)
                .sum();
        if (total <= 0) return;

        // カーソルから消費
        ItemStack cursor = event.getOldCursor().clone();
        cursor.setAmount(Math.max(0, cursor.getAmount() - total));
        event.getView().setCursor(cursor.getAmount() == 0 ? null : cursor);

        economyManager.addWalletBalance(player.getUniqueId(), total);
        player.sendMessage("§a" + total + " ダイヤを財布に収納しました。財布: "
                + economyManager.getWalletBalance(player.getUniqueId()));
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (isWalletItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    // -----------------------------------------------------------------------
    // アクション処理
    // -----------------------------------------------------------------------

    /** スロット8: 財布からダイヤを引き出す。 */
    private void handleWithdraw(Player player, InventoryClickEvent event) {
        int wallet = economyManager.getWalletBalance(player.getUniqueId());
        if (wallet <= 0) {
            player.sendMessage("§c財布に残高がありません。");
            return;
        }
        int withdraw = Math.min(wallet, 64);

        if (event.isShiftClick()) {
            // シフト+クリック: インベントリに直接追加
            economyManager.addWalletBalance(player.getUniqueId(), -withdraw);
            player.getInventory().addItem(new ItemStack(Material.DIAMOND, withdraw));
            player.sendMessage("§b" + withdraw + " ダイヤをインベントリに引き出しました。財布: "
                    + economyManager.getWalletBalance(player.getUniqueId()));
        } else {
            // 通常クリック: カーソルへ渡す
            ItemStack cursor = event.getCursor();
            Material cursorType = (cursor == null) ? Material.AIR : cursor.getType();
            if (cursorType != Material.AIR && cursorType != Material.DIAMOND) {
                player.sendMessage("§cカーソルにダイヤ以外のアイテムがあります。");
                return;
            }
            int held = (cursorType == Material.DIAMOND) ? cursor.getAmount() : 0;
            int canAdd = 64 - held;
            withdraw = Math.min(withdraw, canAdd);
            if (withdraw <= 0) {
                player.sendMessage("§cカーソルがいっぱいです。");
                return;
            }
            economyManager.addWalletBalance(player.getUniqueId(), -withdraw);
            event.getView().setCursor(new ItemStack(Material.DIAMOND, held + withdraw));
            player.sendMessage("§b" + withdraw + " ダイヤをカーソルに引き出しました。財布: "
                    + economyManager.getWalletBalance(player.getUniqueId()));
        }
    }

    /** スロット35: バンドルへのクリック操作を処理する。 */
    private void handleBundle(Player player, InventoryClickEvent event) {
        if (event.isShiftClick()) {
            collectAllDiamonds(player);
            return;
        }
        // カーソルにダイヤがあれば預け入れ
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() == Material.DIAMOND && cursor.getAmount() > 0) {
            int amount = cursor.getAmount();
            economyManager.addWalletBalance(player.getUniqueId(), amount);
            event.getView().setCursor(null);
            player.sendMessage("§a" + amount + " ダイヤを財布に収納しました。財布: "
                    + economyManager.getWalletBalance(player.getUniqueId()));
        }
    }

    /** インベントリ（0〜35）の全ダイヤを財布に収納する。 */
    private void collectAllDiamonds(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.DIAMOND) continue;
            if (i == WITHDRAW_SLOT || i == BUNDLE_SLOT) continue;
            total += item.getAmount();
            player.getInventory().setItem(i, null);
        }
        if (total > 0) {
            economyManager.addWalletBalance(player.getUniqueId(), total);
            player.sendMessage("§aインベントリから " + total + " ダイヤを財布に収納しました。財布: "
                    + economyManager.getWalletBalance(player.getUniqueId()));
        } else {
            player.sendMessage("§cインベントリにダイヤがありません。");
        }
    }

    // -----------------------------------------------------------------------
    // ユーティリティ
    // -----------------------------------------------------------------------

    private boolean isWalletItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(walletKey, PersistentDataType.BYTE);
    }

    private ItemStack createWithdrawItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§lダイヤ引き出し口");
        meta.setLore(List.of(
                "§7左クリック: カーソルに最大64枚引き出し",
                "§7シフト+左クリック: インベントリに最大64枚引き出し"
        ));
        meta.getPersistentDataContainer().set(walletKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBundleItem() {
        ItemStack item = new ItemStack(Material.BUNDLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§l専用バンドル");
        meta.setLore(List.of(
                "§7ダイヤをドラッグ＆ドロップ: 財布に収納",
                "§7ダイヤをシフト+左クリック: そのダイヤを財布に収納",
                "§7このアイテムをシフトクリック: 全ダイヤを一括収納"
        ));
        meta.getPersistentDataContainer().set(walletKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
}
