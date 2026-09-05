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
            sender.sendMessage("§e/perocasino quarry cancel §7… 未確定の角指定を取り消す（稼働中の範囲は残す）");
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
            if (args.length < 2) {
                sender.sendMessage("§c使い方: /perocasino quarry set | cancel");
                return true;
            }
            FileConfiguration cfg = plugin.getConfig();
            if ("cancel".equalsIgnoreCase(args[1])) {
                cfg.set("quarry.pending", null);
                plugin.saveConfig();
                sender.sendMessage("§e未確定の採石場角指定を取り消しました。稼働中の範囲はそのままです。");
                return true;
            }
            if (!"set".equalsIgnoreCase(args[1])) {
                sender.sendMessage("§c使い方: /perocasino quarry set | cancel");
                return true;
            }
            Location loc = player.getLocation();
            World world = loc.getWorld();
            if (world == null) {
                sender.sendMessage("§cワールドが取得できませんでした。");
                return true;
            }

            QuarryCornerSet.Corner here = new QuarryCornerSet.Corner(
                    world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

            if (!cfg.isSet("quarry.pending.x")) {
                cfg.set("quarry.pending.world", here.world());
                cfg.set("quarry.pending.x", here.x());
                cfg.set("quarry.pending.y", here.y());
                cfg.set("quarry.pending.z", here.z());
                plugin.saveConfig();
                sender.sendMessage("§e採石場の §f1点目 §eを記録しました。稼働中の範囲はまだ変えていません。");
                sender.sendMessage("§7もう一度 /perocasino quarry set で対角を確定してください。");
                return true;
            }

            QuarryCornerSet.Corner first = new QuarryCornerSet.Corner(
                    cfg.getString("quarry.pending.world", world.getName()),
                    cfg.getInt("quarry.pending.x"),
                    cfg.getInt("quarry.pending.y"),
                    cfg.getInt("quarry.pending.z"));
            var completed = QuarryCornerSet.complete(first, here);
            if (completed.isEmpty()) {
                cfg.set("quarry.pending.world", here.world());
                cfg.set("quarry.pending.x", here.x());
                cfg.set("quarry.pending.y", here.y());
                cfg.set("quarry.pending.z", here.z());
                plugin.saveConfig();
                sender.sendMessage("§cワールドが違うため 1点目を現在位置に更新しました。同じワールドでもう一度対角を指定してください。");
                return true;
            }

            QuarryCornerSet.Range range = completed.get();
            cfg.set("quarry.world", range.world());
            cfg.set("quarry.min.x", range.minX());
            cfg.set("quarry.min.y", range.minY());
            cfg.set("quarry.min.z", range.minZ());
            cfg.set("quarry.max.x", range.maxX());
            cfg.set("quarry.max.y", range.maxY());
            cfg.set("quarry.max.z", range.maxZ());
            cfg.set("quarry.pending", null);
            plugin.saveConfig();

            sender.sendMessage("§a採石場範囲を登録しました: §f" + range.world()
                    + " §7MIN§f(" + range.minX() + "," + range.minY() + "," + range.minZ() + ")"
                    + " §7MAX§f(" + range.maxX() + "," + range.maxY() + "," + range.maxZ() + ")");
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
            if ("cancel".startsWith(a)) out.add("cancel");
        }
        return out;
    }
}
