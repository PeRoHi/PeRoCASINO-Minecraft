package me.bokan.perocasino.commands;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理者向けコマンド（ルーレット設置・採石場範囲など）。
 */
public class PerocasinoCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final Runnable onReload;

    public PerocasinoCommand(JavaPlugin plugin, Runnable onReload) {
        this.plugin = plugin;
        this.onReload = onReload;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("perocasino.admin")) {
            sender.sendMessage("§c権限がありません。");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/perocasino roulette set §7… 見ている砥石をルーレット拠点に登録");
            sender.sendMessage("§e/perocasino quarry set §7… 採石場の立方体範囲を現在位置の角として登録（2回実行）");
            sender.sendMessage("§e/perocasino reload §7… config.yml を再読込");
            return true;
        }

        String sub = args[0].toLowerCase();
        if ("reload".equals(sub)) {
            plugin.reloadConfig();
            if (onReload != null) {
                onReload.run();
            }
            sender.sendMessage("§aconfig.yml を再読込しました。");
            return true;
        }

        if ("roulette".equals(sub)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cこの操作はプレイヤーから実行してください。");
                return true;
            }
            if (args.length < 2 || !"set".equalsIgnoreCase(args[1])) {
                sender.sendMessage("§c使い方: /perocasino roulette set");
                return true;
            }
            Block target = player.getTargetBlockExact(6);
            if (target == null || target.getType() != Material.GRINDSTONE) {
                sender.sendMessage("§c6ブロック以内の砥石を狙ってください。");
                return true;
            }
            Location loc = target.getLocation();
            FileConfiguration cfg = plugin.getConfig();
            cfg.set("roulette.world", loc.getWorld().getName());
            cfg.set("roulette.x", loc.getBlockX());
            cfg.set("roulette.y", loc.getBlockY());
            cfg.set("roulette.z", loc.getBlockZ());
            plugin.saveConfig();
            sender.sendMessage("§aルーレット拠点を登録しました: §f" + loc.getWorld().getName()
                    + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
            return true;
        }

        if ("quarry".equals(sub)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cこの操作はプレイヤーから実行してください。");
                return true;
            }
            if (args.length < 2 || !"set".equalsIgnoreCase(args[1])) {
                sender.sendMessage("§c使い方: /perocasino quarry set");
                return true;
            }
            Location loc = player.getLocation();
            World world = loc.getWorld();
            if (world == null) {
                sender.sendMessage("§cワールドが取得できませんでした。");
                return true;
            }

            FileConfiguration cfg = plugin.getConfig();
            // 1回目: 一時保存 / 2回目: min/maxへ確定
            String tmp = "quarry._tmpMin";
            if (!cfg.isSet(tmp + ".x")) {
                cfg.set("quarry.world", world.getName());
                cfg.set(tmp + ".world", world.getName());
                cfg.set(tmp + ".x", loc.getBlockX());
                cfg.set(tmp + ".y", loc.getBlockY());
                cfg.set(tmp + ".z", loc.getBlockZ());
                plugin.saveConfig();
                sender.sendMessage("§e採石場の §fMIN §e角を設定しました。もう一度同じコマンドで §fMAX §e角を設定してください。");
                return true;
            }

            String minWorld = cfg.getString(tmp + ".world", world.getName());
            int minX = cfg.getInt(tmp + ".x");
            int minY = cfg.getInt(tmp + ".y");
            int minZ = cfg.getInt(tmp + ".z");
            int maxX = loc.getBlockX();
            int maxY = loc.getBlockY();
            int maxZ = loc.getBlockZ();

            cfg.set("quarry.world", minWorld);
            cfg.set("quarry.min.x", minX);
            cfg.set("quarry.min.y", minY);
            cfg.set("quarry.min.z", minZ);
            cfg.set("quarry.max.x", maxX);
            cfg.set("quarry.max.y", maxY);
            cfg.set("quarry.max.z", maxZ);
            cfg.set(tmp, null);
            plugin.saveConfig();

            sender.sendMessage("§a採石場範囲を登録しました: §f" + minWorld
                    + " §7MIN§f(" + minX + "," + minY + "," + minZ + ")"
                    + " §7MAX§f(" + maxX + "," + maxY + "," + maxZ + ")");
            sender.sendMessage("§7※ もう一度 /perocasino quarry set を2回実行すると範囲を作り直せます。");
            return true;
        }

        sender.sendMessage("§c不明なサブコマンドです。");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String a = args[0].toLowerCase();
            if ("roulette".startsWith(a)) out.add("roulette");
            if ("quarry".startsWith(a)) out.add("quarry");
            if ("reload".startsWith(a)) out.add("reload");
        } else if (args.length == 2 && "roulette".equalsIgnoreCase(args[0])) {
            String a = args[1].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
        } else if (args.length == 2 && "quarry".equalsIgnoreCase(args[0])) {
            String a = args[1].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
        }
        return out;
    }
}
