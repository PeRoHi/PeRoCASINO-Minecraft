package me.bokan.perocasino.listeners;

import me.bokan.perocasino.commands.CasinoCommand;
import me.bokan.perocasino.games.blackjack.BlackjackService;
import me.bokan.perocasino.games.chinchiro.ChinchiroTableService;
import me.bokan.perocasino.games.hilo.HiLoService;
import me.bokan.perocasino.games.slot.SlotMachineService;
import me.bokan.perocasino.roulette.RoulettePhase;
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
    private final SlotMachineService slotMachineService;
    private final BlackjackService blackjackService;
    private final HiLoService hiLoService;
    private final ChinchiroTableService chinchiroTableService;
    private final RouletteBetMenuListener rouletteBetMenu;

    public CasinoMenuListener(LoanMenuListener loanListener, Plugin plugin, SlotMachineService slotMachineService,
                              BlackjackService blackjackService, HiLoService hiLoService,
                              ChinchiroTableService chinchiroTableService,
                              RouletteBetMenuListener rouletteBetMenu) {
        this.loanListener = loanListener;
        this.plugin = plugin;
        this.slotMachineService = slotMachineService;
        this.blackjackService = blackjackService;
        this.hiLoService = hiLoService;
        this.chinchiroTableService = chinchiroTableService;
        this.rouletteBetMenu = rouletteBetMenu;
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
            case 19 ->
                plugin.getServer().getScheduler().runTask(plugin, () -> slotMachineService.openGui(player));
            case 22 ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (RouletteBetMenuListener.getHubPhase() != RoulettePhase.BETTING) {
                        player.sendMessage("§cルーレット進行中はベットできません。");
                        return;
                    }
                    rouletteBetMenu.openBetGui(player);
                });
            case 25 ->
                plugin.getServer().getScheduler().runTask(plugin, () -> hiLoService.openFromMenu(player));
            case 28 ->
                plugin.getServer().getScheduler().runTask(plugin, () -> blackjackService.openJoinConfirm(player));
            case 37 ->
                plugin.getServer().getScheduler().runTask(plugin, () -> chinchiroTableService.openJoinConfirm(player));
            case 49 -> player.closeInventory();
            // 13 (SHOP), 16 (SABOTAGE) は今後実装
        }
    }
}
