package me.bokan.perocasino.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import me.bokan.perocasino.roulette.RoulettePhase;

import java.util.HashMap;
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
    private final Map<UUID, ItemStack[]> savedBets = new HashMap<>();
    
    // 【新規】全ベット（特殊枠）として預けられたダイヤの個数データ
    private final Map<UUID, Integer> allInBets = new HashMap<>();

    /** 現在開いているベットGUI（自動ルーレットの精算対象） */
    private final Map<UUID, Inventory> openBetInventories = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;

    private static volatile RoulettePhase hubPhase = RoulettePhase.BETTING;

    public RouletteBetMenuListener(JavaPlugin plugin) {
        this.plugin = plugin;
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
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);

        if (savedBets.containsKey(player.getUniqueId())) {
            gui.setContents(savedBets.get(player.getUniqueId()));
        }

        // 隠しバンドル（表示更新メソッドを呼ぶ）
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

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        ItemStack currentItem = event.getCurrentItem();

        // 自動ルーレット：ベット受付中以外は盤面操作をロック
        if (getHubPhase() != RoulettePhase.BETTING) {
            event.setCancelled(true);
            return;
        }

        // 1. 手持ちインベントリからのシフトクリックでの不正な移動を防ぐ
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            if (slot >= 54 && currentItem != null && currentItem.getType() == Material.DIAMOND) {
                event.setCancelled(true);
                return;
            }
        }

        // GUI側（上半分）の操作
        if (slot >= 0 && slot < 54) {
            
            // 2. 隠しバンドルの特殊アクション
            if (slot == HIDDEN_BUNDLE_SLOT && isHiddenBundleItem(currentItem)) {
                event.setCancelled(true); // バンドル自体の移動を禁止

                // ※ここに後で「ルーレットが回転中なら return する」というロック処理を追加する
                
                if (event.getClick() == ClickType.SHIFT_LEFT) {
                    performAllIn(player, event.getInventory());
                } else if (event.getClick() == ClickType.LEFT) {
                    withdrawAllIn(player, event.getInventory());
                }
                return;
            }

            // 3. 通常のベット枠（ダイヤのみ許可）
            if (BET_SLOTS.contains(slot)) {
                ItemStack cursor = event.getCursor();
                if (cursor != null && cursor.getType() != Material.AIR && cursor.getType() != Material.DIAMOND) {
                    event.setCancelled(true);
                }
            } else {
                // 許可エリア以外のクリック禁止
                event.setCancelled(true);
            }
        }
    }

    // 全ベット（預け入れ）処理
    private void performAllIn(Player player, Inventory gui) {
        int addAmount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) {
                addAmount += item.getAmount();
                item.setAmount(0); // インベントリから回収
            }
        }

        if (addAmount > 0) {
            UUID uuid = player.getUniqueId();
            int current = allInBets.getOrDefault(uuid, 0);
            allInBets.put(uuid, current + addAmount); // データとして保存
            
            updateHiddenBundle(uuid, gui); // 表示を更新
            player.sendMessage("§a手持ちのダイヤをすべて特殊枠に預けました！");
        } else {
            player.sendMessage("§cインベントリに預けるダイヤがありません。");
        }
    }

    // 引き出し処理
    private void withdrawAllIn(Player player, Inventory gui) {
        UUID uuid = player.getUniqueId();
        int amount = allInBets.getOrDefault(uuid, 0);

        if (amount > 0) {
            // ダイヤをインベントリに返却
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(Material.DIAMOND, amount));
            
            // インベントリが満タンで入り切らなかった場合は足元に落とす
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
                player.sendMessage("§eインベントリが満タンのため、一部のダイヤが足元に落ちました。");
            }
            
            allInBets.put(uuid, 0); // データを0にリセット
            updateHiddenBundle(uuid, gui); // 表示を更新
            player.sendMessage("§b特殊枠のダイヤを引き出しました。");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(GUI_TITLE)) {
            if (getHubPhase() != RoulettePhase.BETTING) {
                // 回転中に閉じるとベットが宙に浮くので、強制的に開き直す
                if (event.getPlayer() instanceof Player player) {
                    player.sendMessage("§cルーレット進行中はGUIを閉じられません。");
                    Bukkit.getScheduler().runTaskLater(
                            plugin,
                            () -> openBetGui(player),
                            1L
                    );
                }
                return;
            }
            savedBets.put(event.getPlayer().getUniqueId(), event.getInventory().getContents());
            openBetInventories.remove(event.getPlayer().getUniqueId());
        }
    }

    // 後でRouletteManagerからデータを回収するためのゲッター
    public Map<UUID, Integer> getAllInBets() {
        return allInBets;
    }
}