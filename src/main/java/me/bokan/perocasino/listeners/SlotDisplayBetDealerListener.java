package me.bokan.perocasino.listeners;

import me.bokan.perocasino.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/**
 * 設置スロット用：掛け金設定ディーラー（Villager）とGUI。
 */
public final class SlotDisplayBetDealerListener implements Listener {

    private static final String BET_TITLE = "§0§lSLOT BET";

    private final JavaPlugin plugin;
    private final EconomyManager economy;
    private final NamespacedKey dealerKey;

    public SlotDisplayBetDealerListener(JavaPlugin plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.dealerKey = new NamespacedKey(plugin, "slot_display_bet_dealer");
        applyConfiguredDealerNpcSettings();
    }

    /** config の UUID に一致するディーラー村人がいれば AI/重力を無効化（再起動後も固定）。 */
    public void applyConfiguredDealerNpcSettings() {
        String raw = plugin.getConfig().getString("slot-display.bet-dealer.uuid", "");
        if (raw == null || raw.isBlank()) return;
        try {
            UUID id = UUID.fromString(raw.trim());
            Entity e = Bukkit.getEntity(id);
            if (e instanceof Villager v) {
                configureDealerNpc(v);
                tagDealer(v);
            }
        } catch (Exception ignored) {
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDealerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        if (!isDealer(villager)) return;
        event.setCancelled(true);
        openBet(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDealerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (!isDealer(villager)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!BET_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (event.getView().getTopInventory() != event.getClickedInventory()) return;

        int bet = switch (event.getSlot()) {
            case 10 -> 1;
            case 11 -> 2;
            case 12 -> 5;
            case 13 -> 10;
            case 14 -> 32;
            case 15 -> 64;
            case 16 -> 128;
            case 22 -> 0; // clear
            default -> -1;
        };
        if (bet < 0) return;

        economy.setSlotDisplayBet(player.getUniqueId(), bet);
        if (bet == 0) {
            player.sendMessage("§e[SLOT] 掛け金を未設定にしました。");
        } else {
            player.sendMessage("§a[SLOT] 掛け金を §b" + bet + " §aに設定しました（次に変更するまで固定）。");
        }
        player.closeInventory();
    }

    private void openBet(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, BET_TITLE);
        inv.setItem(10, betItem(1));
        inv.setItem(11, betItem(2));
        inv.setItem(12, betItem(5));
        inv.setItem(13, betItem(10));
        inv.setItem(14, betItem(32));
        inv.setItem(15, betItem(64));
        inv.setItem(16, betItem(128));
        inv.setItem(22, clearItem());
        player.openInventory(inv);
    }

    private static ItemStack betItem(int amount) {
        ItemStack it = new ItemStack(Material.DIAMOND);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§b掛け金: " + amount);
        meta.setLore(List.of("§7クリックで掛け金を固定します。", "§7スピン開始時に財布から徴収します。"));
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack clearItem() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§c未設定に戻す");
        meta.setLore(List.of("§7掛け金を0（未設定）にします。"));
        it.setItemMeta(meta);
        return it;
    }

    private void tagDealer(Villager villager) {
        villager.getPersistentDataContainer().set(dealerKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
    }

    private boolean isDealer(Villager villager) {
        // まずPDCで判定（再起動後の保険）
        if (villager.getPersistentDataContainer().has(dealerKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
            return true;
        }
        // config uuid でも判定
        String raw = plugin.getConfig().getString("slot-display.bet-dealer.uuid", "");
        if (raw == null || raw.isBlank()) return false;
        try {
            UUID id = UUID.fromString(raw.trim());
            return villager.getUniqueId().equals(id);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void configureDealerNpc(Villager villager) {
        villager.setAI(false);
        villager.setGravity(false);
        villager.setCustomName("§6Slot Dealer");
        villager.setCustomNameVisible(true);
        villager.setProfession(Villager.Profession.CLERIC);
    }
}

