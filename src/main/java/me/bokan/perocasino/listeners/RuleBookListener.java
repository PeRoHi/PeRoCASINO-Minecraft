package me.bokan.perocasino.listeners;

import me.bokan.perocasino.ui.RuleBookFactory;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * ルールブック（記入済みの本）をホットバー左端(0)に固定配布し、移動やドロップを防ぐ。
 */
public class RuleBookListener implements Listener {

    private static final int RULEBOOK_SLOT = 0; // 左下（ホットバー左端）

    private final Plugin plugin;
    private final NamespacedKey ruleBookKey;

    public RuleBookListener(Plugin plugin) {
        this.plugin = plugin;
        this.ruleBookKey = RuleBookFactory.key(plugin);
    }

    public void setupRuleBook(Player player) {
        player.getInventory().setItem(RULEBOOK_SLOT, RuleBookFactory.create(plugin));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        setupRuleBook(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        setupRuleBook(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isRuleBook);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isRuleBook(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // ホットバーキー入替（本がスワップ対象なら禁止）
        if (event.getAction() == InventoryAction.HOTBAR_SWAP && event.getHotbarButton() == RULEBOOK_SLOT) {
            event.setCancelled(true);
            return;
        }

        // プレイヤーインベントリ操作のみ（コンテナGUI側を巻き込まない）
        if (!(event.getClickedInventory() instanceof PlayerInventory)) return;

        int slot = event.getSlot();

        // クリエでも動かせない（消失や複製事故を避ける）
        if (slot == RULEBOOK_SLOT) {
            event.setCancelled(true);
            // 右クリックで本を読む動作はバニラ側に任せたいが、クリックイベントでは開かない。
            return;
        }

        // 本を掴んで別スロットへ置こうとしたら禁止
        ItemStack cursor = event.getCursor();
        if (isRuleBook(cursor)) {
            event.setCancelled(true);
        }

        // 別スロットの本を触ろうとしている（本が何らかの理由で移動していた場合）も禁止
        ItemStack current = event.getCurrentItem();
        if (isRuleBook(current)) {
            event.setCancelled(true);
        }

        // 数字キーで0番へ入れる/0番から出すも禁止
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbar = event.getHotbarButton();
            if (hotbar == RULEBOOK_SLOT) {
                event.setCancelled(true);
            }
        }

        // Creativeのミドルクリック等で生成されるのも抑止
        if (player.getGameMode() == GameMode.CREATIVE && isRuleBook(current)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        // プレイヤーインベントリに対するドラッグで0番を狙っていたら禁止
        Map<Integer, ItemStack> newItems = event.getNewItems();
        if (!newItems.containsKey(RULEBOOK_SLOT)) return;

        boolean targetsRuleBookSlot = event.getRawSlots().stream().anyMatch(rawSlot -> {
            try {
                return event.getView().convertSlot(rawSlot) == RULEBOOK_SLOT
                        && event.getView().getInventory(rawSlot) instanceof PlayerInventory;
            } catch (Exception e) {
                return false;
            }
        });
        if (!targetsRuleBookSlot) return;

        event.setCancelled(true);
    }

    private boolean isRuleBook(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType() != Material.WRITTEN_BOOK) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(ruleBookKey, PersistentDataType.BYTE);
    }
}

