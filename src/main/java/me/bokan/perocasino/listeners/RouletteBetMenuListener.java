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

import java.util.ArrayList;
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

    /** 特殊枠の必要倍率シーケンス: 2→4→6→10 を全て当てると 7777倍。 */
    private static final int[] SPECIAL_SEQUENCE = {2, 4, 6, 10};
    /** 特殊枠の最終当選倍率。 */
    private static final int SPECIAL_JACKPOT_MULTIPLIER = 7777;

    // 盤面に置かれたダイヤの保存用
    private final Map<UUID, ItemStack[]> savedBets = new HashMap<>();

    /** 現在開いているベットGUI（自動ルーレットの精算対象） */
    private final Map<UUID, Inventory> openBetInventories = new ConcurrentHashMap<>();

    /** SPINNING突入時にベットを確定して保管する内部データ。停止確定時にここから精算する。 */
    private final Map<UUID, Map<Integer, Integer>> lockedBets = new HashMap<>();

    /** 特殊枠（連勝枠）の状態 */
    private final Map<UUID, SpecialBet> specialBets = new HashMap<>();

    /** 特殊枠がルーレット開始後の判定中かを追跡（通常枠はBETTING中なら自由に操作可） */
    private final Set<UUID> lockedSpecialBets = ConcurrentHashMap.newKeySet();

    /** 特殊枠の状態（連勝＆預け入れ） */
    static class SpecialBet {
        int amount;   // 預け入れ枚数
        int step;     // 0..SPECIAL_SEQUENCE.length-1（次に必要な段）
    }

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
        // 念のため、ベット枠以外に紛れた可能性のあるアイテムをクリーンしてから開く
        sanitizeNonBetSlots(gui);

        // 隠しバンドル（表示更新メソッドを呼ぶ）
        updateHiddenBundle(player.getUniqueId(), gui);

        player.openInventory(gui);
        openBetInventories.put(player.getUniqueId(), gui);
    }

    /** GUI内の「ベット枠 / 隠しバンドル」以外のスロットを必ず空にする。 */
    private void sanitizeNonBetSlots(Inventory gui) {
        if (gui == null) return;
        for (int i = 0; i < gui.getSize(); i++) {
            if (i == HIDDEN_BUNDLE_SLOT) continue;
            if (BET_SLOTS.contains(i)) continue;
            gui.setItem(i, null);
        }
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
            SpecialBet sb = specialBets.get(uuid);
            if (sb != null && sb.amount > 0) m.merge(SPECIAL_JACKPOT_MULTIPLIER, sb.amount, Integer::sum);
            if (!m.isEmpty()) out.put(uuid, m);
        }
        return out;
    }

    /** 精算後にGUI内ベットと特殊枠をクリアする（開いているGUIだけ対象） */
    public void clearAllOpenBets() {
        for (Inventory inv : openBetInventories.values()) {
            if (inv == null) continue;
            for (int slot : BET_SLOTS) inv.setItem(slot, null);
            sanitizeNonBetSlots(inv);
            updateHiddenBundle(null, inv);
        }
        specialBets.clear();
        savedBets.clear();
        lockedSpecialBets.clear();
    }

    /**
     * BETTING → SPINNING 直前に呼ぶ。
     * - 開いているGUIと、閉じて保存されている savedBets の両方から
     *   BET_SLOTS のダイヤを没収して内部データへ確定保存する。
     * - 確定後、対応するスロットは空にする（盤面に賭けが残らない）。
     * 注意: 特殊枠は別データ（specialBets）で連勝管理しているため、
     *       lockedBets には含めない。
     */
    public void lockBetsForSpin() {
        lockedBets.clear();

        // 1) 現在開いているGUIから集計＆没収（合わせて非ベット枠もクリーン）
        for (Map.Entry<UUID, Inventory> e : openBetInventories.entrySet()) {
            UUID uuid = e.getKey();
            Inventory inv = e.getValue();
            if (inv == null) continue;
            sanitizeNonBetSlots(inv);
            Map<Integer, Integer> m = lockedBets.computeIfAbsent(uuid, k -> new HashMap<>());
            for (int slot : BET_SLOTS) {
                ItemStack it = inv.getItem(slot);
                if (it != null && it.getType() == Material.DIAMOND) {
                    int mult = multiplierForSlot(slot);
                    if (mult > 0) m.merge(mult, it.getAmount(), Integer::sum);
                    inv.setItem(slot, null);
                } else if (it != null) {
                    // BET_SLOTS にダイヤ以外が入り込んでいる場合はクリア（潜在的不正回避）
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
                } else if (it != null) {
                    contents[slot] = null;
                }
            }
        }

        // 3) 特殊枠を持っているプレイヤーだけ、ルーレット開始後の特殊枠操作をロックする
        for (Map.Entry<UUID, SpecialBet> e : specialBets.entrySet()) {
            if (e.getValue() != null && e.getValue().amount > 0) lockedSpecialBets.add(e.getKey());
        }

        // 4) 開いているGUIの表示を更新
        for (Inventory inv : openBetInventories.values()) {
            if (inv != null) updateHiddenBundle(null, inv);
        }
    }

    /**
     * 結果倍率に応じて精算する。
     * - 通常枠: 一致倍率の賭け枚数 × 倍率を払戻（手持ち→溢れは財布）。それ以外は没収。
     * - 特殊枠: 必要シーケンス(2→4→6→10)の現在ステップに一致したらステップを進める。
     *           4段全成立で 7777倍払戻。途中で外したら没収。
     */
    public void settleLockedBets(int resultMultiplier,
                                 me.bokan.perocasino.economy.EconomyManager economy) {
        // 通常枠の精算
        for (Map.Entry<UUID, Map<Integer, Integer>> e : lockedBets.entrySet()) {
            UUID uuid = e.getKey();
            Map<Integer, Integer> m = e.getValue();
            if (m == null) continue;

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
            } else if (totalBet > 0) {
                if (p != null && p.isOnline()) {
                    p.sendMessage("§c[ルーレット] §f結果: §e" + resultMultiplier + "x §c外れ §7(没収: " + totalBet + "個)");
                }
            }
        }
        lockedBets.clear();

        // 特殊枠の精算（連勝管理）
        for (UUID uuid : new ArrayList<>(specialBets.keySet())) {
            SpecialBet sb = specialBets.get(uuid);
            if (sb == null || sb.amount <= 0) {
                specialBets.remove(uuid);
                continue;
            }
            int needed = SPECIAL_SEQUENCE[Math.min(sb.step, SPECIAL_SEQUENCE.length - 1)];
            Player p = Bukkit.getPlayer(uuid);
            if (resultMultiplier == needed) {
                sb.step++;
                if (sb.step >= SPECIAL_SEQUENCE.length) {
                    int payout = sb.amount * SPECIAL_JACKPOT_MULTIPLIER;
                    if (p != null && p.isOnline()) {
                        int toInv = addDiamondsToInventory(p, payout);
                        int overflow = payout - toInv;
                        if (overflow > 0 && economy != null) {
                            economy.addWalletBalance(uuid, overflow);
                        }
                        p.sendMessage("§6§l[特殊枠] §a4連続成立！ §b" + payout + "§a の払戻 §7(手持ち+" + toInv
                                + (overflow > 0 ? " / 財布+" + overflow : "") + ")");
                    } else if (economy != null) {
                        economy.addWalletBalance(uuid, payout);
                    }
                    specialBets.remove(uuid);
                    lockedSpecialBets.remove(uuid);
                } else {
                    if (p != null && p.isOnline()) {
                        int next = SPECIAL_SEQUENCE[sb.step];
                        p.sendMessage("§6[特殊枠] §a" + needed + "x 通過！ §7(次の必要倍率: §e" + next + "x§7, 残り "
                                + (SPECIAL_SEQUENCE.length - sb.step) + "段)");
                    }
                }
            } else {
                if (p != null && p.isOnline()) {
                    p.sendMessage("§c[特殊枠] §f結果: §e" + resultMultiplier + "x §c失敗 (必要: §e" + needed
                            + "x§c) §7没収: " + sb.amount + "個");
                }
                specialBets.remove(uuid);
                lockedSpecialBets.remove(uuid);
            }
        }
    }

    /** 次ラウンドのために、開いているGUIと保存されているGUI内容のベット枠をクリアする。 */
    public void resetForNextRound() {
        for (Inventory inv : openBetInventories.values()) {
            if (inv == null) continue;
            for (int slot : BET_SLOTS) inv.setItem(slot, null);
            sanitizeNonBetSlots(inv);
            updateHiddenBundle(null, inv);
        }
        for (ItemStack[] contents : savedBets.values()) {
            if (contents == null) continue;
            for (int slot : BET_SLOTS) {
                if (slot < 0 || slot >= contents.length) continue;
                contents[slot] = null;
            }
            // 非ベット枠も保険でクリア
            for (int i = 0; i < contents.length; i++) {
                if (i == HIDDEN_BUNDLE_SLOT) continue;
                if (BET_SLOTS.contains(i)) continue;
                contents[i] = null;
            }
        }
        // 特殊枠の連勝状態は継続。amountだけは継続(=ロックは解けない)
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
            meta.setDisplayName("§6§l特殊ベット (2→4→6→10連勝で§e7777倍§6)");

            int count = 0;
            int step = 0;
            if (uuid != null) {
                SpecialBet sb = specialBets.get(uuid);
                if (sb != null) {
                    count = sb.amount;
                    step = sb.step;
                }
            }
            int needNext = SPECIAL_SEQUENCE[Math.min(step, SPECIAL_SEQUENCE.length - 1)];

            meta.setLore(List.of(
                    "§7現在の預け入れ数: §b" + count + "個",
                    "§7連勝進捗: §e" + step + "§7/§e" + SPECIAL_SEQUENCE.length + "  §7次の必要倍率: §e" + needNext + "x",
                    "§f",
                    "§e§l[ 操作方法 ]",
                    "§7SHIFT + 左クリック: §a手持ちのダイヤをすべて預ける",
                    "§7左クリック: §c預けたダイヤを引き出す",
                    "§f",
                    "§c§l※注意※",
                    "§7ルーレット開始後は、特殊枠の勝敗が",
                    "§7確定するまで追加・引き出しできません！"
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
        UUID uuid = player.getUniqueId();
        int slot = event.getRawSlot();
        ItemStack currentItem = event.getCurrentItem();
        boolean phaseLocked = (getHubPhase() != RoulettePhase.BETTING);
        boolean specialLocked = lockedSpecialBets.contains(uuid);

        // フェーズ外/ベットロック中は GUI 側の操作（上段）を一切禁止
        if (slot >= 0 && slot < 54) {
            // 隠しバンドルだけは「ロック中ならキャンセルのみで他に何もしない」
            if (slot == HIDDEN_BUNDLE_SLOT && isHiddenBundleItem(currentItem)) {
                event.setCancelled(true);
                if (phaseLocked || specialLocked) {
                    if (phaseLocked) player.sendMessage("§cベット締切後は操作できません。");
                    else player.sendMessage("§c特殊枠は勝敗が確定するまで追加・引き出しできません。");
                    return;
                }
                if (event.getClick() == ClickType.SHIFT_LEFT) {
                    performAllIn(player, event.getInventory());
                } else if (event.getClick() == ClickType.LEFT) {
                    withdrawAllIn(player, event.getInventory());
                }
                return;
            }

            if (phaseLocked) {
                event.setCancelled(true);
                return;
            }

            // 通常のベット枠（ダイヤのみ許可）
            if (BET_SLOTS.contains(slot)) {
                ItemStack cursor = event.getCursor();
                if (cursor != null && cursor.getType() != Material.AIR && cursor.getType() != Material.DIAMOND) {
                    event.setCancelled(true);
                    return;
                }
            } else {
                // 許可エリア以外のクリック禁止
                event.setCancelled(true);
                return;
            }
        }

        // 下段（プレイヤーインベントリ側）からのシフトクリックは、
        // バニラ任せだと非ベット枠へ入ることがあるので、BET_SLOTSへだけ手動投入する。
        if (slot >= 54) {
            if ((event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)) {
                if (phaseLocked) {
                    event.setCancelled(true);
                    return;
                }
                if (currentItem != null && currentItem.getType() == Material.DIAMOND) {
                    event.setCancelled(true);
                    ItemStack moving = currentItem.clone();
                    int inserted = addToBetSlots(event.getInventory(), moving);
                    if (inserted > 0) {
                        currentItem.setAmount(currentItem.getAmount() - inserted);
                        if (currentItem.getAmount() <= 0) {
                            event.setCurrentItem(null);
                        }
                    }
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        UUID uuid = event.getWhoClicked().getUniqueId();
        boolean phaseLocked = (getHubPhase() != RoulettePhase.BETTING);

        if (phaseLocked) {
            event.setCancelled(true);
            return;
        }
        // ドラッグ先が「BET_SLOTS」以外の上段スロットを含む場合は禁止
        for (int raw : event.getRawSlots()) {
            if (raw < 54 && raw != HIDDEN_BUNDLE_SLOT && !BET_SLOTS.contains(raw)) {
                event.setCancelled(true);
                return;
            }
        }
        // ダイヤ以外は上段に置けない
        ItemStack adding = event.getOldCursor();
        if (adding != null && adding.getType() != Material.AIR && adding.getType() != Material.DIAMOND) {
            for (int raw : event.getRawSlots()) {
                if (raw < 54) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private int addToBetSlots(Inventory gui, ItemStack source) {
        if (gui == null || source == null || source.getType() != Material.DIAMOND) return 0;
        int before = source.getAmount();

        // 既存スタックを先に埋める
        for (int slot : BET_SLOTS) {
            if (source.getAmount() <= 0) break;
            ItemStack current = gui.getItem(slot);
            if (current == null || current.getType() == Material.AIR) continue;
            if (current.getType() != Material.DIAMOND) continue;
            int max = current.getMaxStackSize();
            int room = max - current.getAmount();
            if (room <= 0) continue;
            int move = Math.min(room, source.getAmount());
            current.setAmount(current.getAmount() + move);
            source.setAmount(source.getAmount() - move);
        }

        // 空きBET_SLOTSへ新規スタックを作る
        for (int slot : BET_SLOTS) {
            if (source.getAmount() <= 0) break;
            ItemStack current = gui.getItem(slot);
            if (current != null && current.getType() != Material.AIR) continue;
            int move = Math.min(source.getMaxStackSize(), source.getAmount());
            gui.setItem(slot, new ItemStack(Material.DIAMOND, move));
            source.setAmount(source.getAmount() - move);
        }

        return before - source.getAmount();
    }

    // 全ベット（預け入れ）処理
    private void performAllIn(Player player, Inventory gui) {
        UUID uuid = player.getUniqueId();
        SpecialBet sb = specialBets.get(uuid);
        if (lockedSpecialBets.contains(uuid)) {
            player.sendMessage("§c特殊枠は勝敗が確定するまで追加できません。");
            return;
        }
        int addAmount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) {
                addAmount += item.getAmount();
                item.setAmount(0);
            }
        }

        if (addAmount > 0) {
            SpecialBet nsb = specialBets.computeIfAbsent(uuid, k -> new SpecialBet());
            nsb.amount += addAmount;
            // ルーレット開始前の追加入金ならstepは維持（通常は0）
            updateHiddenBundle(uuid, gui);
            player.sendMessage("§a手持ちのダイヤをすべて特殊枠に預けました！ §7(連勝シーケンス: 2→4→6→10 で§e7777倍§7)");
        } else {
            player.sendMessage("§cインベントリに預けるダイヤがありません。");
        }
    }

    // 引き出し処理
    private void withdrawAllIn(Player player, Inventory gui) {
        UUID uuid = player.getUniqueId();
        SpecialBet sb = specialBets.get(uuid);
        if (sb == null || sb.amount <= 0) {
            player.sendMessage("§e特殊枠に預け入れはありません。");
            return;
        }
        if (lockedSpecialBets.contains(uuid)) {
            player.sendMessage("§c特殊枠は勝敗が確定するまで引き出せません。");
            return;
        }
        int amount = sb.amount;
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(Material.DIAMOND, amount));
        if (!leftover.isEmpty()) {
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
            player.sendMessage("§eインベントリが満タンのため、一部のダイヤが足元に落ちました。");
        }
        specialBets.remove(uuid);
        updateHiddenBundle(uuid, gui);
        player.sendMessage("§b特殊枠のダイヤを引き出しました。");
    }

    private boolean noActiveNormalBet(UUID uuid) {
        Inventory inv = openBetInventories.get(uuid);
        if (inv != null) {
            for (int s : BET_SLOTS) {
                ItemStack it = inv.getItem(s);
                if (it != null && it.getType() == Material.DIAMOND && it.getAmount() > 0) return false;
            }
        }
        ItemStack[] saved = savedBets.get(uuid);
        if (saved != null) {
            for (int s : BET_SLOTS) {
                if (s < 0 || s >= saved.length) continue;
                ItemStack it = saved[s];
                if (it != null && it.getType() == Material.DIAMOND && it.getAmount() > 0) return false;
            }
        }
        return true;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(GUI_TITLE)) {
            if (getHubPhase() != RoulettePhase.BETTING) {
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
            UUID uuid = event.getPlayer().getUniqueId();
            Inventory inv = event.getInventory();
            // 保存前に非ベット枠を必ずクリーン
            sanitizeNonBetSlots(inv);
            savedBets.put(uuid, inv.getContents());
            openBetInventories.remove(uuid);

        }
    }

    /**
     * 互換目的のゲッター。新実装では特殊枠を SpecialBet で持つため、
     * 「現在の預け入れ枚数」だけを参照したい古い呼び出し向けに合算したマップを返す。
     */
    public Map<UUID, Integer> getAllInBets() {
        Map<UUID, Integer> out = new HashMap<>();
        for (Map.Entry<UUID, SpecialBet> e : specialBets.entrySet()) {
            if (e.getValue() != null) out.put(e.getKey(), e.getValue().amount);
        }
        return out;
    }
}