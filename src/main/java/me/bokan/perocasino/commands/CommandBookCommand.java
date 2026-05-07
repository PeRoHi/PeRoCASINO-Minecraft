package me.bokan.perocasino.commands;

import me.bokan.perocasino.ui.CommandBookFactory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class CommandBookCommand implements CommandExecutor {

    private final Plugin plugin;

    public CommandBookCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーから実行してください。");
            return true;
        }
        if (CommandBookFactory.giveIfMissing(player, plugin)) {
            player.sendMessage("§aコマンド集を配布しました。");
        } else {
            player.sendMessage("§7既にコマンド集を持っています。");
        }
        return true;
    }
}
