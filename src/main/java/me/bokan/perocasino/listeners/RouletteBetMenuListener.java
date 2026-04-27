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

    /** SPINNING突入時にベットを確定して保管する内部データ。停止確定時にここから精算する。 */
    private final Map<UUID, Map<Integer, Integer>> lockedBets = new HashMap<>();

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

    /**
     * BET_SLOTS のスロット位置から倍率を決定する。
     * 配置仕様:
     * 11/20/29/38 => 2x
     * 12/21/30/39 => 4x
     * 13/22/31/40 => 6x
     * 14/23/32/41 => 10x
     * 15/24/33/42 => 20x
     */
    public static int multiplierForSlot(int slot) {
        if (!BET_SLOTS.contains(slot)) return 0;
        int col = slot % 9; // 11..15,20..24,... は col=2..6
        return switch (col) {
            case 2 -> 2;
            case 3 -> 4;
            case 4 -> 6;
            case 5 -> 10;
            case 6 -> 20;
            default -> 0;
        };
    }

    /**
     * 現在開いているベットGUIから、倍率ごとの賭け枚数を集計する。
     * - BET_SLOTS 内のダイヤのみ対象
     * - それ以外のスロットのダイヤはベットとして扱わない（精算対象外）
     */
    public Map<UUID, Map<Integer, Integer>> snapshotBetsByMultiplier() {
        Map<UUID, Map<Integer, Integer>> out = new HashMap<>();
        for (Map.Entry<UUID, Inventory> e : openBetInventories.entrySet()) {
            UUID uuid = e.getKey();
            Inventory inv = e.getValue();
            if (inv == null) continue;
            Map<Integer, Integer> m = new HashMap<>();
            for (int slot : BET_SLOTS) {
                ItemStack it = inv.getItem(slot);
                if (it != null && it.getType() == Material.DIAMOND) {
                    int mult = multiplierForSlot(slot);
                    if (mult > 0) m.merge(mult, it.getAmount(), Integer::sum);
                }
            }
            int allIn = allInBets.getOrDefault(uuid, 0);
            if (allIn > 0) m.merge(54, allIn, Integer::sum); // 特殊枠（54扱い）
            if (!m.isEmpty()) out.put(uuid, m);
        }
        return out;
    }

    /** 精算後にGUI内ベットと特殊枠をクリアする（開いているGUIだけ対象） */
    public void clearAllOpenBets() {
        for (Inventory inv : openBetInventories.values()) {
            if (inv == null) continue;
            for (int slot : BET_SLOTS) inv.setItem(slot, null);
            // 表示更新（特殊枠が0になるので反映）
            updateHiddenBundle(null, inv);
        }
        allInBets.clear();
        savedBets.clear();
    }

    /**
     * BETTING → SPINNING 直前に呼ぶ。
     * - 開いているGUIと、閉じて保存されている savedBets の両方から
     *   BET_SLOTS のダイヤを没収して内部データへ確定保存する。
     * - 特殊枠(allInBets)も確定する。
     * - 確定後、対応するスロットは空にする（盤面に賭けが残らない）。
     */
    public void lockBetsForSpin() {
        lockedBets.clear();

        // 1) 現在開いているGUIから集計＆没収
        for (Map.Entry<UUID, Inventory> e : openBetInventories.entrySet()) {
            UUID uuid = e.getKey();
            Inventory inv = e.getValue();
            if (inv == null) continue;
            Map<Integer, Integer> m = lockedBets.computeIfAbsent(uuid, k -> new HashMap<>());
            for (int slot : BET_SLOTS) {
                ItemStack it = inv.getItem(slot);
                if (it != null && it.getType() == Material.DIAMOND) {
                    int mult = multiplierForSlot(slot);
                    if (mult > 0) m.merge(mult, it.getAmount(), Integer::sum);
                    inv.setItem(slot, null);
                }
            }
        }

        // 2) 閉じられて保存されている内容（savedBets）からも回収
        for (Map.Entry<UUID, ItemStack[]> e : savedBets.entrySet()) {
            UUID uuid = e.getKey();
            ItemStack[] contents = e.getValue();
            if (contents == null) continue;
            Map<Integer, Integer> m = lockedBets.computeIfAbsent(uuid, k -> new HashMap<>());
            for (int slot : BET_SLOTS) {
                if (slot < 0 || slot >= contents.length) continue;
                ItemStack it = contents[slot];
                if (it != null && it.getType() == Material.DIAMOND) {
                    int mult = multiplierForSlot(slot);
                    if (mult > 0) m.merge(mult, it.getAmount(), Integer::sum);
                    contents[slot] = null;
                }
            }
        }

        // 3) 特殊枠（allInBets）も「54」キーとして確定
        for (Map.Entry<UUID, Integer> e : allInBets.entrySet()) {
            int amount = e.getValue() == null ? 0 : e.getValue();
            if (amount <= 0) continue;
            lockedBets.computeIfAbsent(e.getKey(), k -> new HashMap<>())
                    .merge(54, amount, Integer::sum);
        }
        allInBets.clear();

        // 4) 開いているGUIの表示を更新（特殊枠表示を0に）
        for (Inventory inv : openBetInventories.values()) {
            if (inv != null) updateHiddenBundle(null, inv);
        }
    }

    /**
     * 結果倍率に応じて精算する。
     * - 一致する倍率枠の賭け枚数 × 倍率 を払戻（手持ち優先、溢れは財布）
     * - その他はすべて没収（=データ削除のみ）
     * - 戻り値: メッセージ用に各プレイヤーへ通知済み
     */
    public void settleLockedBets(int resultMultiplier,
                                 me.bokan.perocasino.economy.EconomyManager economy) {
        if (lockedBets.isEmpty()) return;

        for (Map.Entry<UUID, Map<Integer, Integer>> e : lockedBets.entrySet()) {
            UUID uuid = e.getKey();
            Map<Integer, Integer> m = e.getValue();
            if (m == null || m.isEmpty()) continue;

            int totalBet = m.values().stream().mapToInt(Integer::intValue).sum();
            int win = m.getOrDefault(resultMultiplier, 0);

            Player p = Bukkit.getPlayer(uuid);
            if (win > 0) {
                int payout = win * resultMultiplier;
                if (p != null && p.isOnline()) {
                    int toInv = addDiamondsToInventory(p, payout);
                    int overflow = payout - toInv;
                    if (overflow > 0 && economy != null) {
                        economy.addWalletBalance(uuid, overflow);
                    }
                    p.sendMessage("§a[ルーレット] §f結果: §e" + resultMultiplier + "x §a当たり！ §f払戻 §b"
                            + payout + "§f（手持ち+" + toInv + (overflow > 0 ? " / 財布+" + overflow : "") + "）");
                } else if (economy != null) {
                    economy.addWalletBalance(uuid, payout);
                }
            } else {
                if (p != null && p.isOnline()) {
                    p.sendMessage("§c[ルーレット] §f結果: §e" + resultMultiplier + "x §c外れ §7(没収: " + totalBet + "個)");
                }
            }
        }
        lockedBets.clear();
    }

    /** 次ラウンドのために、開いているGUIと保存されているGUI内容のベット枠をクリアする。 */
    public void resetForNextRound() {
        for (Inventory inv : openBetInventories.values()) {
            if (inv == null) continue;
            for (int slot : BET_SLOTS) inv.setItem(slot, null);
            updateHiddenBundle(null, inv);
        }
        for (ItemStack[] contents : savedBets.values()) {
            if (contents == null) continue;
            for (int slot : BET_SLOTS) {
                if (slot < 0 || slot >= contents.length) continue;
                contents[slot] = null;
            }
        }
        allInBets.clear();
    }

    private static int addDiamondsToInventory(Player player, int amount) {
        if (amount <= 0) return 0;
        int remaining = amount;
        // ItemStack のスタック上限(64)を考慮しつつ複数スタックに分けて投入
        while (remaining > 0) {
            int n = Math.min(64, remaining);
            ItemStack stack = new ItemStack(Material.DIAMOND, n);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (leftover.isEmpty()) {
                remaining -= n;
            } else {
                int left = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                int placed = n - left;
                remaining -= placed;
                break;
            }
        }
        return amount - remaining;
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