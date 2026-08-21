package me.bokan.perocasino.commands;

import me.bokan.perocasino.games.chinchiro.ChinchiroDiceService;
import me.bokan.perocasino.games.chinchiro.ChinchiroHandEvaluator;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * プレイヤー向け: サイコロを振って {@link ChinchiroDiceService} で表示する。
 */
public final class ChinchiroCommand implements CommandExecutor {

    private static final String PERM_ROLL = "perocasino.chinchiro.roll";

    private final JavaPlugin plugin;
    private final ChinchiroDiceService diceService;

    public ChinchiroCommand(JavaPlugin plugin, ChinchiroDiceService diceService) {
        this.plugin = plugin;
        this.diceService = diceService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cプレイヤーから実行してください。");
            return true;
        }
        if (!player.hasPermission(PERM_ROLL)) {
            player.sendMessage("§c権限がありません。");
            return true;
        }

        if (args.length == 0 || !"roll".equalsIgnoreCase(args[0])) {
            player.sendMessage("§e/chinchiro roll §7… サイコロを3個振ります（表示領域が必要）。");
            return true;
        }

        if (!plugin.isEnabled()) {
            return true;
        }

        int[] tops = diceService.rollThreeDice(player);
        if (tops.length != 3) {
            return true;
        }

        player.sendMessage("§6[チンチロ] §f出目: §e" + tops[0] + " §7| §e" + tops[1] + " §7| §e" + tops[2]
                + " §7（§f" + ChinchiroHandEvaluator.describeJapanese(tops) + "§7）");

        try {
            TextComponent head = new TextComponent(TextComponent.fromLegacyText("§7もう一度: "));
            TextComponent link = new TextComponent(TextComponent.fromLegacyText("§a§nサイコロを振る"));
            link.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chinchiro roll"));
            link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("§aクリックで /chinchiro roll").create()));
            TextComponent tail = new TextComponent(TextComponent.fromLegacyText(" §7（チャットから）"));
            player.spigot().sendMessage(head, link, tail);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Chinchiro] クリック用チャット送信に失敗: " + t.getMessage());
            player.sendMessage("§7もう一度: §f/chinchiro roll");
        }

        return true;
    }
}
