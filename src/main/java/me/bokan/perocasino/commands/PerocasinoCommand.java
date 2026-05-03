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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
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
            sender.sendMessage("§e/perocasino blackjack dealer set §7… 見ている村人をブラックジャックディーラーに登録");
            sender.sendMessage("§e/perocasino hilo dealer set|summon §7… H&Lディーラーを設定/召喚");
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

        if ("blackjack".equals(sub)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cこの操作はプレイヤーから実行してください。");
                return true;
            }
            if (args.length < 3 || !"dealer".equalsIgnoreCase(args[1]) || !"set".equalsIgnoreCase(args[2])) {
                sender.sendMessage("§c使い方: /perocasino blackjack dealer set");
                return true;
            }
            Villager villager = player.getNearbyEntities(8, 8, 8).stream()
                    .filter(e -> e instanceof Villager)
                    .map(e -> (Villager) e)
                    .min(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getEyeLocation())))
                    .orElse(null);
            if (villager == null) {
                sender.sendMessage("§c8ブロック以内の村人を狙ってください。");
                return true;
            }
            FileConfiguration cfg = plugin.getConfig();
            cfg.set("blackjack.dealer.uuid", villager.getUniqueId().toString());
            cfg.set("blackjack.dealer.world", villager.getWorld().getName());
            cfg.set("blackjack.dealer.x", villager.getLocation().getX());
            cfg.set("blackjack.dealer.y", villager.getLocation().getY());
            cfg.set("blackjack.dealer.z", villager.getLocation().getZ());
            plugin.saveConfig();
            if (onReload != null) {
                onReload.run();
            }
            sender.sendMessage("§aブラックジャックディーラーを登録しました: §f" + villager.getUniqueId());
            return true;
        }

        if ("hilo".equals(sub)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cこの操作はプレイヤーから実行してください。");
                return true;
            }
            if (args.length < 2 || !"dealer".equalsIgnoreCase(args[1])) {
                sender.sendMessage("§c使い方: /perocasino hilo dealer set|summon");
                return true;
            }
            if (args.length >= 3 && "summon".equalsIgnoreCase(args[2])) {
                Villager villager = (Villager) player.getWorld().spawnEntity(player.getLocation(), EntityType.VILLAGER);
                villager.setCustomName("§6H&L Dealer");
                villager.setCustomNameVisible(true);
                villager.setProfession(Villager.Profession.LIBRARIAN);
                configureHiLoDealerNpc(villager);
                saveHiLoDealer(villager);
                sender.sendMessage("§aH&Lディーラーを召喚・登録しました: §f" + villager.getUniqueId());
                return true;
            }
            if (args.length >= 3 && "set".equalsIgnoreCase(args[2])) {
                Villager villager = player.getNearbyEntities(8, 8, 8).stream()
                        .filter(e -> e instanceof Villager)
                        .map(e -> (Villager) e)
                        .min(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getEyeLocation())))
                        .orElse(null);
                if (villager == null) {
                    sender.sendMessage("§c8ブロック以内の村人を狙ってください。");
                    return true;
                }
                configureHiLoDealerNpc(villager);
                saveHiLoDealer(villager);
                sender.sendMessage("§aH&Lディーラーを登録しました: §f" + villager.getUniqueId());
                return true;
            }
            sender.sendMessage("§c使い方: /perocasino hilo dealer set|summon");
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
            // 次回セットし直せるように min を一旦消す
            cfg.set(pathBase + "min", null);
            plugin.saveConfig();

            sender.sendMessage("§a採石場範囲を登録しました: §f" + world.getName()
                    + " §7MIN§f(" + minX + "," + minY + "," + minZ + ")"
                    + " §7MAX§f(" + maxX + "," + maxY + "," + maxZ + ")");
            sender.sendMessage("§7※ もう一度 /perocasino quarry set を2回実行すると範囲を作り直せます。");
            return true;
        }

        sender.sendMessage("§c不明なサブコマンドです。");
        return true;
    }

    /** H&amp;L ディーラー用NPC：移動・重力を無効化（サーバー再起動後は手動で再設定が必要な場合あり）。 */
    private void configureHiLoDealerNpc(Villager villager) {
        villager.setAI(false);
        villager.setGravity(false);
    }

    private void saveHiLoDealer(Villager villager) {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("hilo.dealer.uuid", villager.getUniqueId().toString());
        cfg.set("hilo.dealer.world", villager.getWorld().getName());
        cfg.set("hilo.dealer.x", villager.getLocation().getX());
        cfg.set("hilo.dealer.y", villager.getLocation().getY());
        cfg.set("hilo.dealer.z", villager.getLocation().getZ());
        plugin.saveConfig();
        if (onReload != null) {
            onReload.run();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String a = args[0].toLowerCase();
            if ("roulette".startsWith(a)) out.add("roulette");
            if ("blackjack".startsWith(a)) out.add("blackjack");
            if ("hilo".startsWith(a)) out.add("hilo");
            if ("quarry".startsWith(a)) out.add("quarry");
            if ("reload".startsWith(a)) out.add("reload");
        } else if (args.length == 2 && "roulette".equalsIgnoreCase(args[0])) {
            String a = args[1].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
        } else if (args.length == 2 && "blackjack".equalsIgnoreCase(args[0])) {
            String a = args[1].toLowerCase();
            if ("dealer".startsWith(a)) out.add("dealer");
        } else if (args.length == 3 && "blackjack".equalsIgnoreCase(args[0]) && "dealer".equalsIgnoreCase(args[1])) {
            String a = args[2].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
        } else if (args.length == 2 && "hilo".equalsIgnoreCase(args[0])) {
            String a = args[1].toLowerCase();
            if ("dealer".startsWith(a)) out.add("dealer");
        } else if (args.length == 3 && "hilo".equalsIgnoreCase(args[0]) && "dealer".equalsIgnoreCase(args[1])) {
            String a = args[2].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
            if ("summon".startsWith(a)) out.add("summon");
        } else if (args.length == 2 && "quarry".equalsIgnoreCase(args[0])) {
            String a = args[1].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
        }
        return out;
    }
}
