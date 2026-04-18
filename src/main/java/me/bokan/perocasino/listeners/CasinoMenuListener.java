package me.bokan.perocasino.listeners;

import me.bokan.perocasino.commands.CasinoCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.Plugin;

/**
 * カジノメインメニューGUIのクリックイベントを処理するリスナー。
 */
public class CasinoMenuListener implements Listener {

    private final LoanMenuListener loanListener;
    private final Plugin plugin;

    public CasinoMenuListener(LoanMenuListener loanListener, Plugin plugin) {
        this.loanListener = loanListener;
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!CasinoCommand.GUI_TITLE.equals(event.getView().getTitle())) return;

        // GUI 内のアイテムは一切持ち出させない
        event.setCancelled(true);

        // トップインベントリ（GUI 側）のクリックのみ処理
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        switch (event.getSlot()) {
            case 10 ->
                // LOAN GUI へ遷移（1tick 後に開いて同 tick でのダブルオープンを回避）
                plugin.getServer().getScheduler().runTask(plugin, () -> loanListener.openGui(player));
            case 49 -> player.closeInventory();
            // 13 (SHOP), 16 (SABOTAGE) は今後実装
        }
    }
}
