package me.bokan.perocasino.commands;

import me.bokan.perocasino.games.hilo.HiLoService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 進行中の H&amp;L で High/Low をチャット以外から選ぶ。
 */
public final class HiLoSelectCommand implements CommandExecutor {

    private final HiLoService hiLoService;

    public HiLoSelectCommand(HiLoService hiLoService) {
        this.hiLoService = hiLoService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーから実行してください。");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§c使い方: /hilo select <high|low>");
            return true;
        }
        String raw = args[0].trim().toLowerCase();
        HiLoService.GuessSelection sel = switch (raw) {
            case "high", "h", "hi" -> HiLoService.GuessSelection.HIGH;
            case "low", "l", "lo" -> HiLoService.GuessSelection.LOW;
            default -> null;
        };
        if (sel == null) {
            player.sendMessage("§chigh または low を指定してください。");
            return true;
        }
        hiLoService.trySelectGuess(player, sel);
        return true;
    }
}
