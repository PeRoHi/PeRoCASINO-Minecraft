package me.bokan.perocasino.commands;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.FluidCollisionMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import me.bokan.perocasino.games.chinchiro.ChinchiroDiceService;
import me.bokan.perocasino.games.slotdisplay.SlotDisplayService;
import me.bokan.perocasino.roulette.RouletteDisplayService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 管理者向けコマンド（ルーレット設置・採石場範囲など）。
 */
public class PerocasinoCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final Runnable onReload;
    private final SlotDisplayService slotDisplayService;
    private final ChinchiroDiceService chinchiroDiceService;

    public PerocasinoCommand(JavaPlugin plugin, Runnable onReload, SlotDisplayService slotDisplayService,
                             ChinchiroDiceService chinchiroDiceService) {
        this.plugin = plugin;
        this.onReload = onReload;
        this.slotDisplayService = slotDisplayService;
        this.chinchiroDiceService = chinchiroDiceService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("perocasino.admin")) {
            sender.sendMessage("§c権限がありません。");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/perocasino roulette set §7… 見ている砥石をルーレット拠点に登録");
            sender.sendMessage("§e/perocasino roulette display set §7… 見ているブロック面にルーレット表示(ItemDisplay)を設置");
            sender.sendMessage("§e/perocasino roulette display remove §7… ルーレット表示(ItemDisplay)を削除");
            sender.sendMessage("§e/perocasino blackjack dealer set|summon §7… ブラックジャックディーラーを設定/召喚");
            sender.sendMessage("§e/perocasino hilo dealer set|summon §7… H&Lディーラーを設定/召喚");
            sender.sendMessage("§e/perocasino quarry set §7… 採石場の立方体範囲を現在位置の角として登録（2回実行）");
            sender.sendMessage("§e/perocasino slot create <id> §7… 設置スロット（TextDisplay）を現在位置に登録");
            sender.sendMessage("§e/perocasino slot remove <id> §7… 設置スロットを設定から削除");
            sender.sendMessage("§e/perocasino slot list §7… 設置スロット一覧");
            sender.sendMessage("§e/perocasino slot dealer set|summon §7… スロット掛け金ディーラーを設定/召喚");
            sender.sendMessage("§e/perocasino chinchiro region set §7… サイコロ3個の出現範囲（2回: MIN→MAX 角）");
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
                sender.sendMessage("§c使い方: /perocasino roulette display remove");
                return true;
            }

            String action = args[1].toLowerCase(Locale.ROOT);

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

            if ("display".equals(action)) {
                if (args.length < 3) {
                    sender.sendMessage("§c使い方: /perocasino roulette display set");
                    sender.sendMessage("§c使い方: /perocasino roulette display remove");
                    return true;
                }
                String subAction = args[2].toLowerCase(Locale.ROOT);

                if ("remove".equals(subAction)) {
                    RouletteDisplayService display = new RouletteDisplayService(plugin);
                    display.reloadFromConfig();
                    display.removeDisplay();
                    sender.sendMessage("§aルーレット表示(ItemDisplay)を削除しました。");
                    return true;
                }

                if (!"set".equals(subAction)) {
                    sender.sendMessage("§c使い方: /perocasino roulette display set");
                    sender.sendMessage("§c使い方: /perocasino roulette display remove");
                    return true;
                }

                Block target = player.getTargetBlockExact(8, FluidCollisionMode.NEVER);
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
                display.removeDisplay();
                display.setAnchor(anchor, face);
                sender.sendMessage("§aルーレット表示(ItemDisplay)を設置しました。");
                sender.sendMessage("§7※ /perocasino reload で確実に復元されます。");
                return true;
            }

            sender.sendMessage("§c使い方: /perocasino roulette set");
            sender.sendMessage("§c使い方: /perocasino roulette display set");
            sender.sendMessage("§c使い方: /perocasino roulette display remove");
            return true;
        }

        if ("blackjack".equals(sub)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cこの操作はプレイヤーから実行してください。");
                return true;
            }
            if (args.length < 2 || !"dealer".equalsIgnoreCase(args[1])) {
                sender.sendMessage("§c使い方: /perocasino blackjack dealer set|summon");
                return true;
            }

            if (args.length >= 3 && "summon".equalsIgnoreCase(args[2])) {
                Villager villager = (Villager) player.getWorld().spawnEntity(player.getLocation(), EntityType.VILLAGER);
                villager.setCustomName("§6Blackjack Dealer");
                villager.setCustomNameVisible(true);
                villager.setProfession(Villager.Profession.CLERIC);
                configureBlackjackDealerNpc(villager);
                saveBlackjackDealer(villager);
                sender.sendMessage("§aブラックジャックディーラーを召喚・登録しました: §f" + villager.getUniqueId());
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
                configureBlackjackDealerNpc(villager);
                saveBlackjackDealer(villager);
                sender.sendMessage("§aブラックジャックディーラーを登録しました: §f" + villager.getUniqueId());
                return true;
            }
            sender.sendMessage("§c使い方: /perocasino blackjack dealer set|summon");
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

        if ("slot".equals(sub)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cこの操作はプレイヤーから実行してください。");
                return true;
            }
            if (slotDisplayService == null) {
                sender.sendMessage("§c設置スロット機能が初期化されていません。");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§c使い方: /perocasino slot <create|remove|list|dealer> ...");
                return true;
            }
            String op = args[1].toLowerCase(Locale.ROOT);
            FileConfiguration cfg = plugin.getConfig();

            if ("dealer".equals(op)) {
                if (args.length < 3) {
                    sender.sendMessage("§c使い方: /perocasino slot dealer set|summon");
                    return true;
                }
                String dop = args[2].toLowerCase(Locale.ROOT);
                if ("summon".equals(dop)) {
                    Villager villager = (Villager) player.getWorld().spawnEntity(player.getLocation(), EntityType.VILLAGER);
                    villager.setCustomName("§6Slot Dealer");
                    villager.setCustomNameVisible(true);
                    villager.setProfession(Villager.Profession.CLERIC);
                    villager.setAI(false);
                    villager.setGravity(false);

                    String base = "slot-display.bet-dealer";
                    cfg.set(base + ".uuid", villager.getUniqueId().toString());
                    cfg.set(base + ".world", player.getWorld().getName());
                    cfg.set(base + ".x", player.getLocation().getBlockX());
                    cfg.set(base + ".y", player.getLocation().getBlockY());
                    cfg.set(base + ".z", player.getLocation().getBlockZ());
                    plugin.saveConfig();
                    sender.sendMessage("§aスロット掛け金ディーラーを召喚・登録しました: §f" + villager.getUniqueId());
                    return true;
                }
                if ("set".equals(dop)) {
                    Villager villager = player.getNearbyEntities(8, 8, 8).stream()
                            .filter(e -> e instanceof Villager)
                            .map(e -> (Villager) e)
                            .min(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getEyeLocation())))
                            .orElse(null);
                    if (villager == null) {
                        sender.sendMessage("§c近くに村人が見つかりません。");
                        return true;
                    }
                    villager.setCustomName("§6Slot Dealer");
                    villager.setCustomNameVisible(true);
                    villager.setProfession(Villager.Profession.CLERIC);
                    villager.setAI(false);
                    villager.setGravity(false);

                    String base = "slot-display.bet-dealer";
                    cfg.set(base + ".uuid", villager.getUniqueId().toString());
                    cfg.set(base + ".world", villager.getWorld().getName());
                    cfg.set(base + ".x", villager.getLocation().getBlockX());
                    cfg.set(base + ".y", villager.getLocation().getBlockY());
                    cfg.set(base + ".z", villager.getLocation().getBlockZ());
                    plugin.saveConfig();
                    sender.sendMessage("§aスロット掛け金ディーラーを登録しました: §f" + villager.getUniqueId());
                    return true;
                }
                sender.sendMessage("§c使い方: /perocasino slot dealer set|summon");
                return true;
            }

            if ("create".equals(op)) {
                if (args.length < 3) {
                    sender.sendMessage("§c使い方: /perocasino slot create <id>");
                    return true;
                }
                String idRaw = args[2];
                String id = idRaw.toLowerCase(Locale.ROOT);
                if (!id.matches("[a-z0-9][a-z0-9_-]{0,31}")) {
                    sender.sendMessage("§cID は英小文字・数字・_- で32文字以内にしてください。");
                    return true;
                }
                Location loc = player.getLocation();
                World world = loc.getWorld();
                if (world == null) {
                    sender.sendMessage("§cワールドが取得できませんでした。");
                    return true;
                }
                String pathBase = "slot-display.machines." + id;
                cfg.set(pathBase + ".world", world.getName());
                cfg.set(pathBase + ".x", loc.getBlockX());
                cfg.set(pathBase + ".y", loc.getBlockY());
                cfg.set(pathBase + ".z", loc.getBlockZ());
                cfg.set(pathBase + ".yaw", (double) loc.getYaw());
                cfg.set(pathBase + ".pitch", (double) loc.getPitch());
                cfg.set("slot-display.enabled", true);
                plugin.saveConfig();
                slotDisplayService.reloadFromConfig();
                sender.sendMessage("§a設置スロット §f" + id + " §aを登録しました（§7slot-display.enabled=true§a）。");
                return true;
            }

            if ("remove".equals(op)) {
                if (args.length < 3) {
                    sender.sendMessage("§c使い方: /perocasino slot remove <id>");
                    return true;
                }
                String id = args[2].toLowerCase(Locale.ROOT);
                cfg.set("slot-display.machines." + id, null);
                plugin.saveConfig();
                slotDisplayService.reloadFromConfig();
                sender.sendMessage("§a設置スロット §f" + id + " §aを削除しました。");
                return true;
            }

            if ("list".equals(op)) {
                ConfigurationSection sec = cfg.getConfigurationSection("slot-display.machines");
                if (sec == null || sec.getKeys(false).isEmpty()) {
                    sender.sendMessage("§7登録された設置スロットはありません。");
                    return true;
                }
                sender.sendMessage("§e--- 設置スロット一覧 ---");
                for (String k : sec.getKeys(false)) {
                    sender.sendMessage("§7- §f" + k);
                }
                return true;
            }

            sender.sendMessage("§c不明な slot サブコマンドです。");
            return true;
        }

        if ("chinchiro".equals(sub)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cこの操作はプレイヤーから実行してください。");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§c使い方: /perocasino chinchiro region set");
                return true;
            }
            if (!"region".equalsIgnoreCase(args[1])) {
                sender.sendMessage("§c使い方: /perocasino chinchiro region set");
                return true;
            }
            if (args.length < 3 || !"set".equalsIgnoreCase(args[2])) {
                sender.sendMessage("§c使い方: /perocasino chinchiro region set");
                return true;
            }
            Location loc = player.getLocation();
            World world = loc.getWorld();
            if (world == null) {
                sender.sendMessage("§cワールドが取得できませんでした。");
                return true;
            }
            FileConfiguration cfg = plugin.getConfig();
            String tmp = "chinchiro.dice.region._tmpMin";
            if (!cfg.isSet(tmp + ".x")) {
                cfg.set(tmp + ".world", world.getName());
                cfg.set(tmp + ".x", loc.getBlockX());
                cfg.set(tmp + ".y", loc.getBlockY());
                cfg.set(tmp + ".z", loc.getBlockZ());
                plugin.saveConfig();
                sender.sendMessage("§eチンチロサイコロ領域の §fMIN §e角を設定しました。もう一度同じコマンドで §fMAX §e角を設定してください。");
                return true;
            }

            String minWorld = cfg.getString(tmp + ".world", world.getName());
            int tminX = cfg.getInt(tmp + ".x");
            int tminY = cfg.getInt(tmp + ".y");
            int tminZ = cfg.getInt(tmp + ".z");
            int maxXb = loc.getBlockX();
            int maxYb = loc.getBlockY();
            int maxZb = loc.getBlockZ();

            cfg.set("chinchiro.dice.region.world", minWorld);
            cfg.set("chinchiro.dice.region.min.x", tminX);
            cfg.set("chinchiro.dice.region.min.y", tminY);
            cfg.set("chinchiro.dice.region.min.z", tminZ);
            cfg.set("chinchiro.dice.region.max.x", maxXb);
            cfg.set("chinchiro.dice.region.max.y", maxYb);
            cfg.set("chinchiro.dice.region.max.z", maxZb);
            cfg.set(tmp, null);
            plugin.saveConfig();

            if (chinchiroDiceService != null) {
                chinchiroDiceService.reloadFromConfig();
            }
            sender.sendMessage("§aチンチロサイコロ領域を登録しました: §f" + minWorld
                    + " §7MIN§f(" + tminX + "," + tminY + "," + tminZ + ")"
                    + " §7MAX§f(" + maxXb + "," + maxYb + "," + maxZb + ")");
            sender.sendMessage("§7※ プレイヤーは §f/chinchiro roll §7でサイコロを振れます。");
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

    /** ブラックジャック ディーラー用NPC：移動・重力を無効化。 */
    private void configureBlackjackDealerNpc(Villager villager) {
        villager.setAI(false);
        villager.setGravity(false);
    }

    private void saveBlackjackDealer(Villager villager) {
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
            if ("slot".startsWith(a)) out.add("slot");
            if ("chinchiro".startsWith(a)) out.add("chinchiro");
            if ("reload".startsWith(a)) out.add("reload");
        } else if (args.length == 2 && "slot".equalsIgnoreCase(args[0])) {
            String a = args[1].toLowerCase();
            if ("create".startsWith(a)) out.add("create");
            if ("remove".startsWith(a)) out.add("remove");
            if ("list".startsWith(a)) out.add("list");
            if ("dealer".startsWith(a)) out.add("dealer");
<<<<<<< HEAD
        } else if (args.length == 3 && "slot".equalsIgnoreCase(args[0]) && "dealer".equalsIgnoreCase(args[1])) {
            String a = args[2].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
            if ("summon".startsWith(a)) out.add("summon");
=======
>>>>>>> 674b8d49fc5efea26847c0e00c7ddfff37c46ccf
        } else if (args.length == 2 && "roulette".equalsIgnoreCase(args[0])) {
            String a = args[1].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
            if ("display".startsWith(a)) out.add("display");
        } else if (args.length == 3 && "roulette".equalsIgnoreCase(args[0]) && "display".equalsIgnoreCase(args[1])) {
            String a = args[2].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
            if ("remove".startsWith(a)) out.add("remove");
        } else if (args.length == 2 && "blackjack".equalsIgnoreCase(args[0])) {
            String a = args[1].toLowerCase();
            if ("dealer".startsWith(a)) out.add("dealer");
        } else if (args.length == 3 && "blackjack".equalsIgnoreCase(args[0]) && "dealer".equalsIgnoreCase(args[1])) {
            String a = args[2].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
            if ("summon".startsWith(a)) out.add("summon");
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
        } else if (args.length == 2 && "chinchiro".equalsIgnoreCase(args[0])) {
            String a = args[1].toLowerCase();
            if ("region".startsWith(a)) out.add("region");
        } else if (args.length == 3 && "chinchiro".equalsIgnoreCase(args[0]) && "region".equalsIgnoreCase(args[1])) {
            String a = args[2].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
        }
        return out;
    }
}
