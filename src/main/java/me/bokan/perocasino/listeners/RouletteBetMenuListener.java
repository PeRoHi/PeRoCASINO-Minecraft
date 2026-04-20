package me.bokan.perocasino.listeners;

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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RouletteBetMenuListener implements Listener {

    // \uF808 で左に8ピクセル戻してから画像を描画する
    public static final String GUI_TITLE = "§f\uF808\uE001";

    // ベット可能な4x5マス（左から 2倍, 4倍, 6倍, 10倍, 20倍）のスロット番号
    private static final Set<Integer> BET_SLOTS = Set.of(
            11, 12, 13, 14, 15, // 1段目
            20, 21, 22, 23, 24, // 2段目
            29, 30, 31, 32, 33, // 3段目
            38, 39, 40, 41, 42  // 4段目
    );

    // プレイヤーごとのベット状態（置いたダイヤ）を保存するマップ
    private final Map<UUID, ItemStack[]> savedBets = new HashMap<>();

    public void openBetGui(Player player) {
        UUID uuid = player.getUniqueId();
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);

        // 背景（ベット枠以外）をガラスで埋めてクリックできなくする
        ItemStack bg = createBgItem();
        for (int i = 0; i < 54; i++) {
            if (!BET_SLOTS.contains(i)) {
                gui.setItem(i, bg);
            }
        }

        // 以前のベット（待機時間中に置いたダイヤ）が保存されていれば復元
        if (savedBets.containsKey(uuid)) {
            ItemStack[] saved = savedBets.get(uuid);
            for (int slot : BET_SLOTS) {
                gui.setItem(slot, saved[slot]);
            }
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        int slot = event.getSlot();
        Inventory clickedInv = event.getClickedInventory();

        // 上のGUI（チェスト側）をクリックした場合の制御
        if (clickedInv != null && clickedInv.equals(event.getView().getTopInventory())) {
            // ベット枠以外は操作不可
            if (!BET_SLOTS.contains(slot)) {
                event.setCancelled(true);
                return;
            }
            
            // 置けるのはダイヤのみにする
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR && cursor.getType() != Material.DIAMOND) {
                event.setCancelled(true);
                player.sendMessage("§cダイヤしかベットできません！");
            }
        }
        // 自分のインベントリからのシフトクリック（一括移動）制御
        else if (event.isShiftClick()) {
            ItemStack current = event.getCurrentItem();
            if (current != null && current.getType() != Material.DIAMOND) {
                event.setCancelled(true); // ダイヤ以外はGUIに飛ばせないようにする
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        
        Player player = (Player) event.getPlayer();
        // 閉じた瞬間に、現在のGUIの中身（54マス分の配列）を丸ごと保存する
        savedBets.put(player.getUniqueId(), event.getView().getTopInventory().getContents());
    }

    // 外部（RouletteManagerなど）からベットを回収・リセットするためのメソッド
    public Map<UUID, ItemStack[]> getSavedBets() {
        return savedBets;
    }

    public void clearAllBets() {
        savedBets.clear();
    }

    private ItemStack createBgItem() {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" "); // 名前を空白にして目立たなくする
        item.setItemMeta(meta);
        return item;
    }
}