package me.bokan.perocasino.commands;

import me.bokan.perocasino.economy.EconomyManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BalanceCommand implements CommandExecutor {

    private final EconomyManager economyManager;

    public BalanceCommand(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ使用できます。");
            return true;
        }

        var uuid   = player.getUniqueId();
        int wallet = economyManager.getWalletBalance(uuid);
        int debt   = economyManager.getDebt(uuid);
        if (economyManager.isLoadFailed(uuid)) {
            player.sendMessage(ChatColor.RED + "経済データの読込に失敗したため、残高は表示できません。管理者に連絡してください。");
            return true;
        }

        player.sendMessage(ChatColor.AQUA + "===== PeRoCasino 残高 =====");
        player.sendMessage(ChatColor.WHITE + "財布残高: " + ChatColor.AQUA  + wallet + " ダイヤ");
        player.sendMessage(ChatColor.WHITE + "借金額:   " + ChatColor.RED   + debt   + " ダイヤ");
        player.sendMessage(ChatColor.AQUA + "==========================");

        return true;
    }
}
