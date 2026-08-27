package me.bokan.perocasino.listeners;

import me.bokan.perocasino.economy.EconomyManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;

public class WalletListener implements Listener {

    private static final int WITHDRAW_SLOT = 8;
    private static final int BUNDLE_SLOT   = 35;

    private final EconomyManager economyManager;
    private final NamespacedKey walletKey;

    public WalletListener(EconomyManager economyManager, Plugin plugin) {
        this.economyManager = economyManager;
        this.walletKey = new NamespacedKey(plugin, "wallet_item");
    }

    public void setupWalletItems(Player player) {
        player.getInventory().setItem(WITHDRAW_SLOT, createWithdrawItem());
        player.getInventory().setItem(BUNDLE_SLOT,   createBundleItem());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        setupWalletItems(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isWalletItem);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        setupWalletItems(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getAction() == InventoryAction.HOTBAR_SWAP
                && event.getHotbarButton() == WITHDRAW_SLOT) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getClickedInventory() instanceof PlayerInventory)) return;
        int slot = event.getSlot();

        if (player.getGameMode() == GameMode.CREATIVE
                && (slot == WITHDRAW_SLOT || slot == BUNDLE_SLOT)) {
            event.setCancelled(true);
            return;
        }

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

        if (event.isShiftClick()
                && event.getCurrentItem() != null
                && event.getCurrentItem().getType() == Material.DIAMOND
                && isOwnInventoryView(event)) {
            int amount = event.getCurrentItem().getAmount();
            if (!economyManager.tryDepositWallet(player.getUniqueId(), amount)) {
                player.sendMessage("§c財布が上限のため預け入れできません。");
                return;
            }
            event.setCancelled(true);
            player.getInventory().setItem(slot, null);
            player.sendMessage("§a" + amount + " ダイヤを財布に収納しました。財布: "
                    + economyManager.getWalletBalance(player.getUniqueId()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (!isOwnInventoryView(event.getView().getType())) return;

        int deposited = 0;
        for (int raw : event.getRawSlots()) {
            try {
                if (!(event.getView().getInventory(raw) instanceof PlayerInventory)) continue;
                if (event.getView().convertSlot(raw) != BUNDLE_SLOT) continue;
                ItemStack placed = event.getNewItems().get(raw);
                if (placed != null && placed.getType() == Material.DIAMOND) {
                    deposited += placed.getAmount();
                }
            } catch (IllegalArgumentException ignored) {
                // convertSlot can throw for some raw slots
            }
        }
        if (deposited <= 0) return;

        event.setCancelled(true);

        ItemStack cursor = event.getOldCursor();
        if (cursor == null || cursor.getType() != Material.DIAMOND) return;

        int take = Math.min(deposited, cursor.getAmount());
        if (!economyManager.tryDepositWallet(player.getUniqueId(), take)) {
            player.sendMessage("§c財布が上限のため預け入れできません。");
            return;
        }

        ItemStack next = cursor.clone();
        next.setAmount(cursor.getAmount() - take);
        event.getView().setCursor(next.getAmount() <= 0 ? null : next);

        player.sendMessage("§a" + take + " ダイヤを財布に収納しました。財布: "
                + economyManager.getWalletBalance(player.getUniqueId()));
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (isWalletItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    private void handleWithdraw(Player player, InventoryClickEvent event) {
        UUID uuid = player.getUniqueId();
        int wallet = economyManager.getWalletBalance(uuid);
        if (wallet <= 0) {
            player.sendMessage("§c財布に残高がありません。");
            return;
        }
        int withdraw = Math.min(wallet, 64);

        if (event.isShiftClick()) {
            if (!economyManager.tryWithdrawWallet(uuid, withdraw)) {
                player.sendMessage("§c引き出しに失敗しました。");
                return;
            }
            if (!economyManager.giveDiamondsOrWallet(player, withdraw)) {
                player.sendMessage("§eインベントリと財布の両方に入り切らなかった分は足元に落ちました。");
            } else {
                player.sendMessage("§b" + withdraw + " ダイヤをインベントリに引き出しました。財布: "
                        + economyManager.getWalletBalance(uuid));
            }
        } else {
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
            if (!economyManager.tryWithdrawWallet(uuid, withdraw)) {
                player.sendMessage("§c引き出しに失敗しました。");
                return;
            }
            event.getView().setCursor(new ItemStack(Material.DIAMOND, held + withdraw));
            player.sendMessage("§b" + withdraw + " ダイヤをカーソルに引き出しました。財布: "
                    + economyManager.getWalletBalance(uuid));
        }
    }

    private void handleBundle(Player player, InventoryClickEvent event) {
        if (event.isShiftClick()) {
            collectAllDiamonds(player);
            return;
        }
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() == Material.DIAMOND && cursor.getAmount() > 0) {
            int amount = cursor.getAmount();
            if (!economyManager.tryDepositWallet(player.getUniqueId(), amount)) {
                player.sendMessage("§c財布が上限のため預け入れできません。");
                return;
            }
            event.getView().setCursor(null);
            player.sendMessage("§a" + amount + " ダイヤを財布に収納しました。財布: "
                    + economyManager.getWalletBalance(player.getUniqueId()));
        }
    }

    private void collectAllDiamonds(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.DIAMOND) continue;
            if (i == WITHDRAW_SLOT || i == BUNDLE_SLOT) continue;
            total += item.getAmount();
        }
        if (total <= 0) {
            player.sendMessage("§cインベントリにダイヤがありません。");
            return;
        }
        if (!economyManager.tryDepositWallet(player.getUniqueId(), total)) {
            player.sendMessage("§c財布が上限のため預け入れできません。");
            return;
        }
        for (int i = 0; i < 36; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.DIAMOND) continue;
            if (i == WITHDRAW_SLOT || i == BUNDLE_SLOT) continue;
            player.getInventory().setItem(i, null);
        }
        player.sendMessage("§aインベントリから " + total + " ダイヤを財布に収納しました。財布: "
                + economyManager.getWalletBalance(player.getUniqueId()));
    }

    private static boolean isOwnInventoryView(InventoryClickEvent event) {
        return isOwnInventoryView(event.getView().getType());
    }

    private static boolean isOwnInventoryView(InventoryType type) {
        return type == InventoryType.CRAFTING || type == InventoryType.PLAYER;
    }

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
        meta.setCustomModelData(111);
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
