package me.bokan.perocasino.commands;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.FluidCollisionMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import me.bokan.perocasino.roulette.RouletteDisplayService;
import me.bokan.perocasino.roulette.RouletteBetBoardService;

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
            sender.sendMessage("§e/perocasino roulette remove §7… ルーレット拠点登録を削除");
            sender.sendMessage("§e/perocasino roulette stop §7… ルーレット進行を一時停止");
            sender.sendMessage("§e/perocasino roulette start §7… ルーレット進行を再開");
            sender.sendMessage("§e/perocasino roulette display set §7… 見ているブロック面にルーレット表示(ItemDisplay)を設置");
            sender.sendMessage("§e/perocasino roulette display remove §7… ルーレット表示(ItemDisplay)を削除");
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
            if (args.length < 2) {
                sender.sendMessage("§c使い方: /perocasino roulette set");
                sender.sendMessage("§c使い方: /perocasino roulette display set");
                return true;
            }

            String action = args[1].toLowerCase();
            if ("set".equals(action)) {
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

            if ("board".equals(action)) {
                if (args.length < 3 || !"set".equalsIgnoreCase(args[2])) {
                    sender.sendMessage("§c使い方: /perocasino roulette board set");
                    return true;
                }
                Block target = player.getTargetBlockExact(8);
                if (target == null || target.getType() != Material.GRINDSTONE) {
                    sender.sendMessage("§c8ブロック以内の砥石（左端）を狙ってください。");
                    return true;
                }
                // 盤面は「左端の砥石」から、プレイヤー視点で右方向に5列並んでいる想定
                String facing = RouletteBetBoardService.facingFromPlayerYaw(player.getLocation().getYaw()).name();
                FileConfiguration cfg = plugin.getConfig();
                cfg.set("roulette.board.world", target.getWorld().getName());
                cfg.set("roulette.board.x", target.getX());
                cfg.set("roulette.board.y", target.getY());
                cfg.set("roulette.board.z", target.getZ());
                cfg.set("roulette.board.facing", facing);
                plugin.saveConfig();
                sender.sendMessage("§aルーレット砥石ベット盤（左端）を登録しました: §f" + target.getWorld().getName()
                        + " " + target.getX() + " " + target.getY() + " " + target.getZ()
                        + " §7facing=" + facing);
                sender.sendMessage("§7※ /perocasino reload で反映されます。");
                return true;
            }

            if ("remove".equals(action)) {
                FileConfiguration cfg = plugin.getConfig();
                cfg.set("roulette.world", "");
                cfg.set("roulette.x", 0);
                cfg.set("roulette.y", 0);
                cfg.set("roulette.z", 0);
                plugin.saveConfig();
                sender.sendMessage("§aルーレット拠点登録を削除しました。");
                sender.sendMessage("§7※ /perocasino reload で反映されます。");
                return true;
            }

            if ("stop".equals(action)) {
                FileConfiguration cfg = plugin.getConfig();
                cfg.set("roulette.enabled", false);
                plugin.saveConfig();
                sender.sendMessage("§eルーレット進行を停止しました。");
                sender.sendMessage("§7※ /perocasino reload で反映されます。");
                return true;
            }

            if ("start".equals(action)) {
                FileConfiguration cfg = plugin.getConfig();
                cfg.set("roulette.enabled", true);
                plugin.saveConfig();
                sender.sendMessage("§aルーレット進行を再開しました。");
                sender.sendMessage("§7※ /perocasino reload で反映されます。");
                return true;
            }

            if ("display".equals(action)) {
                if (args.length < 3) {
                    sender.sendMessage("§c使い方: /perocasino roulette display set");
                    sender.sendMessage("§c使い方: /perocasino roulette display remove");
                    return true;
                }
                String subAction = args[2].toLowerCase();

                // display remove
                if ("remove".equals(subAction)) {
                    RouletteDisplayService display = new RouletteDisplayService(plugin);
                    display.reloadFromConfig();
                    display.removeDisplay();
                    sender.sendMessage("§aルーレット表示(ItemDisplay)を削除しました。");
                    return true;
                }

                // display set
                if (!"set".equals(subAction)) {
                    sender.sendMessage("§c使い方: /perocasino roulette display set");
                    sender.sendMessage("§c使い方: /perocasino roulette display remove");
                    return true;
                }
                org.bukkit.block.Block target = player.getTargetBlockExact(8, FluidCollisionMode.NEVER);
                if (target == null || target.getType() == Material.AIR) {
                    sender.sendMessage("§c8ブロック以内のブロックを狙ってください。");
                    return true;
                }
                // 置く面は「プレイヤー側を向く面」
                BlockFace face = target.getFace(player.getLocation().getBlock());
                if (face == null) face = BlockFace.NORTH;
                face = face.getOppositeFace();

                Location anchor = target.getLocation().add(0.5, 0.5, 0.5);

                RouletteDisplayService display = new RouletteDisplayService(plugin);
                display.reloadFromConfig();
                // 貼り替え（移動）: 既存があれば削除してから再設置
                display.removeDisplay();
                display.setAnchor(anchor, face);
                sender.sendMessage("§aルーレット表示(ItemDisplay)を設置しました。");
                sender.sendMessage("§7※ /perocasino reload で確実に復元されます。");
                return true;
            }

            sender.sendMessage("§c使い方: /perocasino roulette set");
            sender.sendMessage("§c使い方: /perocasino roulette board set");
            sender.sendMessage("§c使い方: /perocasino roulette display set");
            sender.sendMessage("§c使い方: /perocasino roulette display remove");
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
            String pathBase = "quarry.";
            if (!cfg.isSet(pathBase + "min.x")) {
                cfg.set(pathBase + "world", world.getName());
                cfg.set(pathBase + "min.x", loc.getBlockX());
                cfg.set(pathBase + "min.y", loc.getBlockY());
                cfg.set(pathBase + "min.z", loc.getBlockZ());
                plugin.saveConfig();
                sender.sendMessage("§e採石場の §fMIN §e角を設定しました。もう一度同じコマンドで §fMAX §e角を設定してください。");
                return true;
            }

            int minX = cfg.getInt(pathBase + "min.x");
            int minY = cfg.getInt(pathBase + "min.y");
            int minZ = cfg.getInt(pathBase + "min.z");
            int maxX = loc.getBlockX();
            int maxY = loc.getBlockY();
            int maxZ = loc.getBlockZ();

            cfg.set(pathBase + "world", world.getName());
            cfg.set(pathBase + "max.x", maxX);
            cfg.set(pathBase + "max.y", maxY);
            cfg.set(pathBase + "max.z", maxZ);
            plugin.saveConfig();

            sender.sendMessage("§a採石場範囲を登録しました: §f" + world.getName()
                    + " §7MIN§f(" + minX + "," + minY + "," + minZ + ")"
                    + " §7MAX§f(" + maxX + "," + maxY + "," + maxZ + ")");
            sender.sendMessage("§7※ 範囲を作り直したい場合は config.yml の quarry.min/max を編集するか、/perocasino quarry reset を実装予定です。");
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
            if ("board".startsWith(a)) out.add("board");
            if ("display".startsWith(a)) out.add("display");
            if ("remove".startsWith(a)) out.add("remove");
            if ("stop".startsWith(a)) out.add("stop");
            if ("start".startsWith(a)) out.add("start");
        } else if (args.length == 2 && "quarry".equalsIgnoreCase(args[0])) {
            String a = args[1].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
        } else if (args.length == 3 && "roulette".equalsIgnoreCase(args[0]) && "display".equalsIgnoreCase(args[1])) {
            String a = args[2].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
            if ("remove".startsWith(a)) out.add("remove");
        } else if (args.length == 3 && "roulette".equalsIgnoreCase(args[0]) && "board".equalsIgnoreCase(args[1])) {
            String a = args[2].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
        }
        return out;
    }
}
