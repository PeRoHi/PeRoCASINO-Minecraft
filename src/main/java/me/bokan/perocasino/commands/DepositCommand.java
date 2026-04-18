package me.bokan.perocasino.commands;

import me.bokan.perocasino.economy.EconomyManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class DepositCommand implements CommandExecutor {

    private final EconomyManager economyManager;

    public DepositCommand(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ使用できます。");
            return true;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType() != Material.DIAMOND) {
            player.sendMessage(ChatColor.RED + "メインハンドにダイヤモンドを持ってください。");
            return true;
        }

        int amount = mainHand.getAmount();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        economyManager.addWalletBalance(player.getUniqueId(), amount);
        int newBalance = economyManager.getWalletBalance(player.getUniqueId());

        player.sendMessage(ChatColor.GREEN + "ダイヤを " + ChatColor.YELLOW + amount
                + ChatColor.GREEN + " 個預け入れました。"
                + ChatColor.WHITE + " 財布残高: " + ChatColor.AQUA + newBalance);

        return true;
    }
}
