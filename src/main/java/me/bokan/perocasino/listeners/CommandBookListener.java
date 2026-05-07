package me.bokan.perocasino.listeners;

import me.bokan.perocasino.ui.CommandBookFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * ログイン時にコマンド集を持っていなければ1冊配布。
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
            if (CommandBookFactory.isCommandBook(it, plugin)) return true;
        }
        return false;
    }
}
