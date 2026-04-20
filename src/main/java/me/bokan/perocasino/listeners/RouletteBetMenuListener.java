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
import org.bukkit.inventory.meta.BundleMeta;

import java.util.*;

public class RouletteBetMenuListener implements Listener {

    public static final String GUI_TITLE = "§f\uF808\uE001"; // 位置調整済みタイトル
    private static final int HIDDEN_BUNDLE_SLOT = 53; // 右下

    // ベット可能な20枠
    private static final Set<Integer> BET_SLOTS = Set.of(
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33,
            38, 39, 40, 41, 42
    );

    private final Map<UUID, ItemStack[]> savedBets = new HashMap<>();

    public void openBetGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);

        if (savedBets.containsKey(player.getUniqueId())) {
            gui.setContents(savedBets.get(player.getUniqueId()));
        }

        // 隠しバンドル（表示更新メソッドを呼ぶ）
        updateHiddenBundle(gui);

        player.openInventory(gui);
    }

    private void updateHiddenBundle(Inventory gui) {
        ItemStack bundle = gui.getItem(HIDDEN_BUNDLE_SLOT);
        if (bundle == null || bundle.getType() != Material.BUNDLE) {
            bundle = new ItemStack(Material.BUNDLE);
        }
        
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l特殊ベット・バンドル");
            // 中身のダイヤを計算して説明文に入れる
            int count = 0;
            for (ItemStack item : meta.getItems()) {
                if (item != null && item.getType() == Material.DIAMOND) count += item.getAmount();
            }
            meta.setLore(List.of("§7現在の保持量: §b" + count + "個", "§e§lSHIFT + LEFT CLICK §7で全ベット！"));
            meta.setCustomModelData(777); // カスタムモデルデータで見た目を変える（リソースパック側で設定）
            bundle.setItemMeta(meta);
        }
        gui.setItem(HIDDEN_BUNDLE_SLOT, bundle);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        ItemStack currentItem = event.getCurrentItem();

        // 1. バンドルへの強制自動回収（シフトクリック）を阻止
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            if (currentItem != null && currentItem.getType() == Material.DIAMOND) {
                // ダイヤをシフトクリックしても、GUI内のバンドルや財布には入らせない
                event.setCancelled(true);
                // 代わりに空いているベット枠に移動させる等の自作処理が必要（一旦キャンセルのみ）
                return;
            }
        }

        // 2. 隠しバンドルの特殊アクション（全入れ全ベット）
        if (slot == HIDDEN_BUNDLE_SLOT && currentItem != null && currentItem.getType() == Material.BUNDLE) {
            if (event.getClick() == ClickType.SHIFT_LEFT) {
                event.setCancelled(true);
                performAllIn(player, event.getInventory());
                return;
            }
        }

        // 3. 許可エリア以外のクリック禁止
        if (slot >= 0 && slot < 54) {
            if (!BET_SLOTS.contains(slot) && slot != HIDDEN_BUNDLE_SLOT) {
                event.setCancelled(true);
            }
        }
    }

    private void performAllIn(Player player, Inventory gui) {
        // インベントリ内の全ダイヤを回収してベットスロットに等分、または順に配置するロジック
        // ここでは例として、インベントリのダイヤをすべて回収して順番に埋めていく
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) {
                for (int betSlot : BET_SLOTS) {
                    ItemStack target = gui.getItem(betSlot);
                    if (target == null || target.getType() == Material.AIR) {
                        gui.setItem(betSlot, item.clone());
                        item.setAmount(0);
                        break;
                    }
                }
            }
        }
        player.sendMessage("§a全ダイヤをベットしました！");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(GUI_TITLE)) {
            savedBets.put(event.getPlayer().getUniqueId(), event.getInventory().getContents());
        }
    }
}