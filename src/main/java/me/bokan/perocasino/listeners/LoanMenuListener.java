package me.bokan.perocasino.listeners;

import me.bokan.perocasino.commands.CasinoCommand;
import me.bokan.perocasino.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LOAN モード選択・借入・返済 GUI の処理。
 *
 * 画面遷移:
 *   カジノメイン → [モード選択] → [借入 / 返済 数値入力] → 確定
 *
 * モード選択 (MODE_TITLE):
 *   Slot 11: 借りる  Slot 15: 返済する  Slot 22: 閉じる
 *
 * 数値入力 共通レイアウト（BORROW_TITLE / REPAY_TITLE）:
 *   Row1: [11]▲+100  [13]▲+10  [15]▲+1
 *   Row2: [20]百の位  [22]十の位  [24]一の位  (紙)
 *   Row3: [29]▼-100  [31]▼-10  [33]▼-1
 *   Row5: [48]戻る                [50]確定
 */
public class LoanMenuListener implements Listener {

    // GUI タイトル
    public static final String MODE_TITLE   = "§0§lLOAN - メニュー";
    public static final String BORROW_TITLE = "§0§lLOAN - 借入額選択";
    public static final String REPAY_TITLE  = "§0§lLOAN - 返済額選択";

    /** 後方互換用エイリアス（CasinoMenuListener が参照することがあれば使用） */
    public static final String GUI_TITLE = BORROW_TITLE;

    private static final int MIN_VALUE   = 0;
    private static final int BORROW_MAX  = 999;

    /** 返済期限（借入確定から 15 分）。 */
    private static final long LOAN_DURATION_MS    = 15 * 60 * 1000L;
    /** 利息適用間隔（5 分）。 */
    private static final long INTEREST_INTERVAL_MS = 5 * 60 * 1000L;

    private final EconomyManager economyManager;
    private final Plugin plugin;

    /** 数値入力中の値（借入・返済どちらも共用）。 */
    private final Map<UUID, Integer> inputValues   = new HashMap<>();
    /** 返済 GUI での最大値（min(debt, wallet)）。 */
    private final Map<UUID, Integer> repayMaxValues = new HashMap<>();

    public LoanMenuListener(EconomyManager economyManager, Plugin plugin) {
        this.economyManager = economyManager;
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // GUI オープナー
    // -----------------------------------------------------------------------

    /** カジノメインから遷移するエントリポイント。モード選択画面を開く。 */
    public void openGui(Player player) {
        player.openInventory(buildModeGui(player));
    }

    private void openBorrowGui(Player player) {
        inputValues.put(player.getUniqueId(), 0);
        player.openInventory(buildInputGui(BORROW_TITLE, 0, BORROW_MAX,
                "§a§l借入する", "§7借りたい金額を選択してください"));
    }

    private void openRepayGui(Player player) {
        int maxRepay = Math.min(
                economyManager.getDebt(player.getUniqueId()),
                economyManager.getWalletBalance(player.getUniqueId())
        );
        UUID uuid = player.getUniqueId();
        repayMaxValues.put(uuid, maxRepay);
        inputValues.put(uuid, 0);

        int debt   = economyManager.getDebt(uuid);
        int wallet = economyManager.getWalletBalance(uuid);
        String subtitle = "§7借金: §c" + debt + " §7 財布: §b" + wallet;
        player.openInventory(buildInputGui(REPAY_TITLE, 0, maxRepay,
                "§a§l返済する", subtitle));
    }

    // -----------------------------------------------------------------------
    // GUI 構築
    // -----------------------------------------------------------------------

    private Inventory buildModeGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, MODE_TITLE);

        int debt   = economyManager.getDebt(player.getUniqueId());
        int wallet = economyManager.getWalletBalance(player.getUniqueId());

        // 借りるボタン
        ItemStack borrow = new ItemStack(Material.GOLD_INGOT);
        ItemMeta bm = borrow.getItemMeta();
        bm.setDisplayName("§6§l借りる");
        bm.setLore(List.of("§7最大 §e" + BORROW_MAX + " §7ダイヤまで借入"));
        borrow.setItemMeta(bm);
        gui.setItem(11, borrow);

        // 返済ボタン
        ItemStack repay = new ItemStack(Material.EMERALD);
        ItemMeta rm = repay.getItemMeta();
        rm.setDisplayName("§a§l返済する");
        rm.setLore(List.of(
                "§7借金: §c" + debt,
                "§7財布: §b" + wallet,
                "§7返済可能: §e" + Math.min(debt, wallet)
        ));
        repay.setItemMeta(rm);
        gui.setItem(15, repay);

        // 閉じるボタン
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("§7[閉じる]");
        close.setItemMeta(cm);
        gui.setItem(22, close);

        return gui;
    }

    /**
     * 借入・返済共用の数値入力 GUI を構築する。
     *
     * @param title    GUI タイトル
     * @param value    初期値
     * @param maxValue 最大値
     * @param confirmLabel 確定ボタンの表示名
     * @param infoLine  情報行（Lore に使用）
     */
    private Inventory buildInputGui(String title, int value, int maxValue,
                                    String confirmLabel, String infoLine) {
        Inventory gui = Bukkit.createInventory(null, 54, title);

        gui.setItem(11, createPane(Material.LIME_STAINED_GLASS_PANE, "§a§l▲ +100"));
        gui.setItem(13, createPane(Material.LIME_STAINED_GLASS_PANE, "§a§l▲ +10"));
        gui.setItem(15, createPane(Material.LIME_STAINED_GLASS_PANE, "§a§l▲ +1"));

        refreshDigits(gui, value);

        gui.setItem(29, createPane(Material.RED_STAINED_GLASS_PANE, "§c§l▼ -100"));
        gui.setItem(31, createPane(Material.RED_STAINED_GLASS_PANE, "§c§l▼ -10"));
        gui.setItem(33, createPane(Material.RED_STAINED_GLASS_PANE, "§c§l▼ -1"));

        // 戻るボタン
        ItemStack back = new ItemStack(Material.RED_WOOL);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§c§l戻る");
        back.setItemMeta(bm);
        gui.setItem(48, back);

        // 確定ボタン
        ItemStack confirm = new ItemStack(Material.DIAMOND);
        ItemMeta fm = confirm.getItemMeta();
        fm.setDisplayName(confirmLabel);
        fm.setLore(List.of(infoLine, "§7最大: §e" + maxValue));
        confirm.setItemMeta(fm);
        gui.setItem(50, confirm);

        return gui;
    }

        // LoanMenuListener.java の refreshDigits メソッド
    private void refreshDigits(Inventory gui, int value) {
        int h = value / 100;
        int t = (value % 100) / 10;
        int o = value % 10;
        
        gui.setItem(20, h == 0 ? new ItemStack(Material.AIR) : createPaper("§e百の位", h));
        gui.setItem(22, t == 0 ? new ItemStack(Material.AIR) : createPaper("§e十の位", t));
        
        // 一の位は 0 でも AIR にせず、常に表示する！
        gui.setItem(24, createPaper("§e一の位", o)); 
    }

    // -----------------------------------------------------------------------
    // イベント処理
    // -----------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (MODE_TITLE.equals(title)) {
            handleModeClick(player, event);
        } else if (BORROW_TITLE.equals(title)) {
            handleInputClick(player, event, true);
        } else if (REPAY_TITLE.equals(title)) {
            handleInputClick(player, event, false);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        // MODE_TITLE は inputValues / repayMaxValues を使わないため対象外。
        // 入力 GUI が閉じた時だけデータを破棄する。
        if (BORROW_TITLE.equals(title) || REPAY_TITLE.equals(title)) {
            UUID uuid = event.getPlayer().getUniqueId();
            inputValues.remove(uuid);
            repayMaxValues.remove(uuid);
        }
    }

    // -----------------------------------------------------------------------
    // モード選択クリック
    // -----------------------------------------------------------------------

    private void handleModeClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        switch (event.getSlot()) {
            case 11 -> plugin.getServer().getScheduler()
                    .runTask(plugin, () -> openBorrowGui(player));
            case 15 -> {
                int debt = economyManager.getDebt(player.getUniqueId());
                if (debt <= 0) {
                    player.sendMessage("§c借金がありません。");
                    return;
                }
                int wallet = economyManager.getWalletBalance(player.getUniqueId());
                if (wallet <= 0) {
                    player.sendMessage("§c財布が空です。ダイヤを入金してから返済してください。");
                    return;
                }
                plugin.getServer().getScheduler()
                        .runTask(plugin, () -> openRepayGui(player));
            }
            case 22 -> player.closeInventory();
        }
    }

    // -----------------------------------------------------------------------
    // 数値入力クリック（借入・返済共通）
    // -----------------------------------------------------------------------

    private void handleInputClick(Player player, InventoryClickEvent event, boolean isBorrow) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        UUID uuid    = player.getUniqueId();
        int current  = inputValues.getOrDefault(uuid, 0);
        int maxValue = isBorrow ? BORROW_MAX : repayMaxValues.getOrDefault(uuid, 0);
        Inventory gui = event.getView().getTopInventory();

        switch (event.getSlot()) {
            case 11 -> applyDelta(uuid, gui, current + 100, maxValue);
            case 13 -> applyDelta(uuid, gui, current + 10,  maxValue);
            case 15 -> applyDelta(uuid, gui, current + 1,   maxValue);
            case 29 -> applyDelta(uuid, gui, current - 100, maxValue);
            case 31 -> applyDelta(uuid, gui, current - 10,  maxValue);
            case 33 -> applyDelta(uuid, gui, current - 1,   maxValue);
            case 48 -> handleBack(player, uuid);
            case 50 -> {
                if (isBorrow) handleBorrowConfirm(player, uuid, current);
                else          handleRepayConfirm(player, uuid, current, maxValue);
            }
        }
    }

    // -----------------------------------------------------------------------
    // デルタ適用
    // -----------------------------------------------------------------------

    private void applyDelta(UUID uuid, Inventory gui, int newValue, int maxValue) {
        int clamped = Math.max(MIN_VALUE, Math.min(maxValue, newValue));
        inputValues.put(uuid, clamped);
        refreshDigits(gui, clamped);
    }

    // -----------------------------------------------------------------------
    // ボタンアクション
    // -----------------------------------------------------------------------

    private void handleBack(Player player, UUID uuid) {
        inputValues.remove(uuid);
        repayMaxValues.remove(uuid);
        plugin.getServer().getScheduler().runTask(plugin, () -> openGui(player));
    }

    private void handleBorrowConfirm(Player player, UUID uuid, int amount) {
        if (amount <= 0) {
            player.sendMessage("§c金額を1以上入力してください。");
            return;
        }
        inputValues.remove(uuid);

        long now = System.currentTimeMillis();
        
        // 【重要】借金を記録する
        economyManager.addDebt(uuid, amount);
        
        // 【ここを追加！】借りた分だけ財布（walletBalance）も増やす
        economyManager.addWalletBalance(uuid, amount);

        economyManager.setLoanDeadline(uuid, now + LOAN_DURATION_MS);
        economyManager.setNextInterestMillis(uuid, now + INTEREST_INTERVAL_MS);

        player.closeInventory();
        player.sendMessage("§c§l[ローン] §f" + amount 
                + " ダイヤを借りました。借金: §c" + economyManager.getDebt(uuid)
                + " §f| 財布に反映されました。"
                + " §f| 5分ごとに利息が加算されます。");
    }

    private void handleRepayConfirm(Player player, UUID uuid, int amount, int maxRepay) {
        if (amount <= 0) {
            player.sendMessage("§c金額を1以上入力してください。");
            return;
        }
        if (amount > maxRepay) {
            player.sendMessage("§c返済額が限度を超えています（最大: " + maxRepay + "）。");
            return;
        }

        inputValues.remove(uuid);
        repayMaxValues.remove(uuid);

        economyManager.addWalletBalance(uuid, -amount);
        economyManager.addDebt(uuid, -amount);

        int remaining = economyManager.getDebt(uuid);
        player.closeInventory();

        if (remaining == 0) {
            economyManager.clearLoanTimer(uuid);
            player.sendMessage("§a§l[完済] §fすべての借金を返済しました！");
        } else {
            player.sendMessage("§a§l[返済] §f" + amount
                    + " ダイヤを返済しました。残り借金: §c" + remaining);
        }
    }

    // -----------------------------------------------------------------------
    // ItemStack ファクトリ
    // -----------------------------------------------------------------------

    private ItemStack createPaper(String label, int digit) {
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, digit));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(label + ": §b" + digit);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
