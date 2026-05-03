package me.bokan.perocasino.listeners;

import me.bokan.perocasino.ui.CommandBookFactory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * 全プレイヤーに「コマンド集」ルールブックを配布（固定はしない）。
 * すでに所持している場合は重複配布しない。
 */
public class CommandBookListener implements Listener {

    private final Plugin plugin;

    public CommandBookListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (hasCommandBook(p)) return;
        p.getInventory().addItem(CommandBookFactory.create(plugin));
    }

    private boolean hasCommandBook(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null) continue;
            if (CommandBookFactory.isCommandBook(it, plugin)) return true;
        }
        return false;
    }
}

