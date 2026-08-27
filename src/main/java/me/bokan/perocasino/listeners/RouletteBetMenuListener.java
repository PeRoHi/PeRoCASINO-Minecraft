package me.bokan.perocasino.listeners;

import me.bokan.perocasino.economy.EconomyManager;
import me.bokan.perocasino.roulette.RoulettePhase;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RouletteBetMenuListener implements Listener {

    public static final String GUI_TITLE = "§f\uF808\uE001"; // 位置調整済みタイトル
    public static final int HIDDEN_BUNDLE_SLOT = 53; // 右下

    // ベット可能な20枠
    public static final Set<Integer> BET_SLOTS = Set.of(
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33,
            38, 39, 40, 41, 42
    );

    // 盤面に置かれたダイヤの保存用
    private final Map<UUID, ItemStack[]> savedBets = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> allInBets = new ConcurrentHashMap<>();

    /** 現在開いているベットGUI（自動ルーレットの精算対象） */
    private final Map<UUID, Inventory> openBetInventories = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;
    private final EconomyManager economy;

    private static volatile RoulettePhase hubPhase = RoulettePhase.BETTING;

    public RouletteBetMenuListener(JavaPlugin plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public static RoulettePhase getHubPhase() {
        return hubPhase;
    }

    public static void setHubPhase(RoulettePhase phase) {
        hubPhase = phase == null ? RoulettePhase.BETTING : phase;
    }

    public Map<UUID, Inventory> getOpenBetInventoriesView() {
        return openBetInventories;
    }

    public void openBetGui(Player player) {
        Inventory existing = openBetInventories.get(player.getUniqueId());
        if (existing != null) {
            if (player.getOpenInventory().getTopInventory() != existing) {
                player.openInventory(existing);
            }
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);

        if (savedBets.containsKey(player.getUniqueId())) {
            gui.setContents(savedBets.get(player.getUniqueId()));
        }

        updateHiddenBundle(player.getUniqueId(), gui);

        player.openInventory(gui);
        openBetInventories.put(player.getUniqueId(), gui);
    }

    private void updateHiddenBundle(UUID uuid, Inventory gui) {
        ItemStack bundle = new ItemStack(Material.PAPER);
        ItemMeta meta = bundle.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6§l特殊ベット (2→4→6→10連勝枠)");
            
            // データから現在の預け入れ数を取得
            int count = allInBets.getOrDefault(uuid, 0);
            
            // 君が求めていた操作説明と警告書きだ！
            meta.setLore(List.of(
                    "§7現在の預け入れ数: §b" + count + "個",
                    "§f",
                    "§e§l[ 操作方法 ]",
                    "§7SHIFT + 左クリック: §a手持ちのダイヤをすべて預ける",
                    "§7左クリック: §c預けたダイヤを引き出す",
                    "§f",
                    "§c§l※注意※",
                    "§7一度預けた状態でルーレットが回り始めたら、",
                    "§7結果が決まるまで取り出すことはできません！"
            ));
            
            // ★超重要：ここが透明化のスイッチ
            meta.setCustomModelData(777); 
            bundle.setItemMeta(meta);
        }
        gui.setItem(HIDDEN_BUNDLE_SLOT, bundle);
    }

    public void refreshHiddenBundle(UUID uuid, Inventory gui) {
        updateHiddenBundle(uuid, gui);
    }

    private boolean isHiddenBundleItem(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (!meta.hasCustomModelData()) return false;
        return meta.getCustomModelData() == 777;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        if (getHubPhase() != RoulettePhase.BETTING) {
            event.setCancelled(true);
            return;
        }

        GuiSafety.cancelOutsideClick(event);
        if (GuiSafety.cancelUnsafeBottomMoves(event)) {
            return;
        }

        int slot = event.getRawSlot();
        ItemStack currentItem = event.getCurrentItem();

        if (slot >= 0 && slot < 54) {
            if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.DOUBLE_CLICK) {
                event.setCancelled(true);
                return;
            }

            if (slot == HIDDEN_BUNDLE_SLOT && isHiddenBundleItem(currentItem)) {
                event.setCancelled(true);
                if (event.getClick() == ClickType.SHIFT_LEFT) {
                    performAllIn(player, event.getInventory());
                } else if (event.getClick() == ClickType.LEFT) {
                    withdrawAllIn(player, event.getInventory());
                }
                return;
            }

            if (BET_SLOTS.contains(slot)) {
                ItemStack cursor = event.getCursor();
                if (cursor != null && cursor.getType() != Material.AIR && cursor.getType() != Material.DIAMOND) {
                    event.setCancelled(true);
                }
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        if (getHubPhase() != RoulettePhase.BETTING) {
            event.setCancelled(true);
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < 0 || raw >= topSize) continue;
            if (!BET_SLOTS.contains(raw)) {
                event.setCancelled(true);
                return;
            }
            ItemStack stacked = event.getNewItems().get(raw);
            if (stacked != null && stacked.getType() != Material.DIAMOND) {
                event.setCancelled(true);
                return;
            }
            if (stacked != null && stacked.getAmount() > 64) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void performAllIn(Player player, Inventory gui) {
        ItemStack[] contents = player.getInventory().getContents();
        int addAmount = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == Material.DIAMOND) {
                addAmount += item.getAmount();
            }
        }

        if (addAmount <= 0) {
            player.sendMessage("§cインベントリに預けるダイヤがありません。");
            return;
        }

        UUID uuid = player.getUniqueId();
        int current = allInBets.getOrDefault(uuid, 0);
        long sum = (long) current + (long) addAmount;
        if (sum > Integer.MAX_VALUE) {
            player.sendMessage("§c特殊枠が上限のためこれ以上預けられません。");
            return;
        }

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.DIAMOND) {
                player.getInventory().setItem(i, null);
            }
        }

        allInBets.put(uuid, (int) sum);
        updateHiddenBundle(uuid, gui);
        player.sendMessage("§a手持ちのダイヤをすべて特殊枠に預けました！");
    }

    private void withdrawAllIn(Player player, Inventory gui) {
        UUID uuid = player.getUniqueId();
        int amount = allInBets.getOrDefault(uuid, 0);
        if (amount <= 0) {
            return;
        }
        allInBets.put(uuid, 0);
        updateHiddenBundle(uuid, gui);
        economy.giveDiamondsOrWallet(player, amount);
        player.sendMessage("§b特殊枠のダイヤを引き出しました。");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        UUID uuid = event.getPlayer().getUniqueId();
        if (getHubPhase() != RoulettePhase.BETTING) {
            if (event.getPlayer() instanceof Player player) {
                player.sendMessage("§cルーレット進行中はGUIを閉じられません。");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    Inventory existing = openBetInventories.get(player.getUniqueId());
                    if (existing != null) {
                        player.openInventory(existing);
                    }
                }, 1L);
            }
            return;
        }
        savedBets.put(uuid, event.getInventory().getContents());
        openBetInventories.remove(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (getHubPhase() == RoulettePhase.BETTING) {
            Inventory open = openBetInventories.remove(uuid);
            if (open != null) {
                savedBets.put(uuid, open.getContents());
            }
        }
    }

    public void returnAllChips() {
        Set<UUID> ids = new HashSet<>();
        ids.addAll(openBetInventories.keySet());
        ids.addAll(savedBets.keySet());
        ids.addAll(allInBets.keySet());
        for (UUID uuid : ids) {
            int chips = allInBets.getOrDefault(uuid, 0);
            Inventory open = openBetInventories.get(uuid);
            if (open != null) {
                chips += countBetDiamonds(open);
            } else {
                ItemStack[] saved = savedBets.get(uuid);
                if (saved != null) {
                    chips += countBetDiamonds(saved);
                }
            }
            if (chips > 0) {
                economy.tryDepositWallet(uuid, chips);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.sendMessage("§eルーレット停止のためベットを財布に戻しました: " + chips);
                }
            }
        }
        allInBets.clear();
        savedBets.clear();
        openBetInventories.clear();
    }

    private static int countBetDiamonds(Inventory inv) {
        int n = 0;
        for (int slot : BET_SLOTS) {
            ItemStack stack = inv.getItem(slot);
            if (stack != null && stack.getType() == Material.DIAMOND) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    private static int countBetDiamonds(ItemStack[] contents) {
        int n = 0;
        for (int slot : BET_SLOTS) {
            if (slot < 0 || slot >= contents.length) continue;
            ItemStack stack = contents[slot];
            if (stack != null && stack.getType() == Material.DIAMOND) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    public Map<UUID, Integer> getAllInBets() {
        return allInBets;
    }
}