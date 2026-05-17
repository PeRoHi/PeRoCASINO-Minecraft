package me.bokan.perocasino.commands;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.FluidCollisionMode;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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

import me.bokan.perocasino.PeRoCasino;
import me.bokan.perocasino.commandwand.CommandWandItems;
import me.bokan.perocasino.games.chinchiro.ChinchiroDiceService;
import me.bokan.perocasino.games.slotdisplay.SlotDisplayService;
import me.bokan.perocasino.roulette.RouletteBetBoardService;
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
            sender.sendMessage("§e/perocasino roulette remove §7… ルーレット拠点登録を削除");
            sender.sendMessage("§e/perocasino roulette stop §7… ルーレット進行を一時停止");
            sender.sendMessage("§e/perocasino roulette start §7… ルーレット進行を再開");
            sender.sendMessage("§e/perocasino roulette board set §7… 砥石ベット盤の左端砥石を登録");
            sender.sendMessage("§e/perocasino roulette display set §7… 見ているブロック面にルーレット表示(ItemDisplay)を設置");
            sender.sendMessage("§e/perocasino roulette display remove §7… ルーレット表示(ItemDisplay)を削除");
            sender.sendMessage("§e/perocasino blackjack dealer set|summon §7… ブラックジャックディーラーを設定/召喚");
            sender.sendMessage("§e/perocasino hilo dealer set|summon §7… H&Lディーラーを設定/召喚");
            sender.sendMessage("§e/perocasino quarry set §7… 採石場の立方体範囲を現在位置の角として登録（2回実行）");
            sender.sendMessage("§e/perocasino slot create <id> §7… 設置スロット（TextDisplay）を現在位置に登録");
            sender.sendMessage("§e/perocasino slot remove <id> §7… 設置スロットを設定から削除");
            sender.sendMessage("§e/perocasino slot list §7… 設置スロット一覧");
            sender.sendMessage("§e/perocasino slot dealer set|summon §7… スロット掛け金ディーラーを設定/召喚");
            sender.sendMessage("§e/perocasino chinchiro dealer set|summon §7… チンチロ卓ディーラー");
            sender.sendMessage("§e/perocasino chinchiro region set §7… サイコロ3個の出現範囲（2回: MIN→MAX 角）");
            sender.sendMessage("§e/perocasino wandchest §7… 見ている位置にコマンド杖一式入りチェストを設置");
            sender.sendMessage("§e/perocasino reload §7… config.yml を再読込");
            return true;
        }

        String sub = args[0].toLowerCase();
        if ("wandchest".equals(sub)) {
            return spawnWandChest(sender);
        }

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
                sender.sendMessage("§c使い方: /perocasino roulette board set");
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
                String subAction = args[2].toLowerCase(Locale.ROOT);

                if ("remove".equals(subAction)) {
                    RouletteDisplayService display = rouletteDisplay();
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

                RouletteDisplayService display = rouletteDisplay();
                display.reloadFromConfig();
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
                sender.sendMessage("§c使い方: /perocasino chinchiro dealer set|summon");
                sender.sendMessage("§c使い方: /perocasino chinchiro region set");
                return true;
            }
            String branch = args[1].toLowerCase(Locale.ROOT);
            if ("dealer".equals(branch)) {
                if (args.length < 3) {
                    sender.sendMessage("§c使い方: /perocasino chinchiro dealer set|summon");
                    return true;
                }
                if ("summon".equalsIgnoreCase(args[2])) {
                    Villager villager = (Villager) player.getWorld().spawnEntity(player.getLocation(), EntityType.VILLAGER);
                    villager.setCustomName("§6Chinchiro Dealer");
                    villager.setCustomNameVisible(true);
                    villager.setProfession(Villager.Profession.MASON);
                    configureChinchiroDealerNpc(villager);
                    saveChinchiroDealer(villager);
                    sender.sendMessage("§aチンチロディーラーを召喚・登録しました: §f" + villager.getUniqueId());
                    return true;
                }
                if ("set".equalsIgnoreCase(args[2])) {
                    Villager villager = player.getNearbyEntities(8, 8, 8).stream()
                            .filter(e -> e instanceof Villager)
                            .map(e -> (Villager) e)
                            .min(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getEyeLocation())))
                            .orElse(null);
                    if (villager == null) {
                        sender.sendMessage("§c8ブロック以内の村人を狙ってください。");
                        return true;
                    }
                    configureChinchiroDealerNpc(villager);
                    saveChinchiroDealer(villager);
                    sender.sendMessage("§aチンチロディーラーを登録しました: §f" + villager.getUniqueId());
                    return true;
                }
                sender.sendMessage("§c使い方: /perocasino chinchiro dealer set|summon");
                return true;
            }
            if (!"region".equals(branch)) {
                sender.sendMessage("§c使い方: /perocasino chinchiro dealer set|summon");
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
            sender.sendMessage("§7※ 卓は村人ディーラーから。単体練習は §f/chinchiro roll §7も利用できます。");
            return true;
        }

        sender.sendMessage("§c不明なサブコマンドです。");
        return true;
    }

    private void configureChinchiroDealerNpc(Villager villager) {
        villager.setAI(false);
        villager.setGravity(false);
    }

    private void saveChinchiroDealer(Villager villager) {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("chinchiro.dealer.uuid", villager.getUniqueId().toString());
        cfg.set("chinchiro.dealer.world", villager.getWorld().getName());
        cfg.set("chinchiro.dealer.x", villager.getLocation().getX());
        cfg.set("chinchiro.dealer.y", villager.getLocation().getY());
        cfg.set("chinchiro.dealer.z", villager.getLocation().getZ());
        plugin.saveConfig();
        if (onReload != null) {
            onReload.run();
        }
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

    private boolean spawnWandChest(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこの操作はプレイヤーから実行してください。");
            return true;
        }

        Block target = player.getTargetBlockExact(8, FluidCollisionMode.NEVER);
        if (target == null) {
            player.sendMessage("§c8ブロック以内のブロックを狙ってください。");
            return true;
        }

        Block placeBlock;
        if (target.getType().isAir() || !target.getType().isSolid()) {
            placeBlock = target;
        } else {
            BlockFace face = target.getFace(player.getLocation().getBlock());
            if (face == null) {
                face = BlockFace.UP;
            }
            placeBlock = target.getRelative(face);
        }

        Material existing = placeBlock.getType();
        if (!existing.isAir() && existing != Material.WATER && existing != Material.CAVE_AIR) {
            player.sendMessage("§cこの場所にはチェストを置けません: §f" + existing);
            return true;
        }

        placeBlock.setType(Material.CHEST, false);
        if (!(placeBlock.getState() instanceof Chest chestState)) {
            player.sendMessage("§cチェストの設置に失敗しました。");
            return true;
        }

        FileConfiguration cfg = plugin.getConfig();
        List<ItemStack> wands = CommandWandItems.allConfiguredWands(cfg);
        if (wands.isEmpty()) {
            player.sendMessage("§ccommand-wand.wands が空です。config.yml を確認してください。");
            placeBlock.setType(Material.AIR, false);
            return true;
        }

        Inventory inv = chestState.getBlockInventory();
        int slot = 0;
        int overflow = 0;
        Location dropAt = placeBlock.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack wand : wands) {
            if (slot < inv.getSize()) {
                inv.setItem(slot++, wand);
            } else {
                placeBlock.getWorld().dropItemNaturally(dropAt, wand);
                overflow++;
            }
        }
        chestState.update(true, false);

        Location loc = placeBlock.getLocation();
        player.sendMessage("§aコマンド杖チェストを設置しました: §f" + loc.getBlockX() + " "
                + loc.getBlockY() + " " + loc.getBlockZ() + " §7(" + wands.size() + "本)");
        if (overflow > 0) {
            player.sendMessage("§eチェストに入り切らなかった杖を §f" + overflow + " §e本ドロップしました。");
        }
        player.sendMessage("§7※ 使用時は §fcommand-wand.enabled: true §7と §fperocasino.commandwand§7 が必要です。");
        return true;
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
            if ("wandchest".startsWith(a)) out.add("wandchest");
        } else if (args.length == 2 && "slot".equalsIgnoreCase(args[0])) {
            String a = args[1].toLowerCase();
            if ("create".startsWith(a)) out.add("create");
            if ("remove".startsWith(a)) out.add("remove");
            if ("list".startsWith(a)) out.add("list");
            if ("dealer".startsWith(a)) out.add("dealer");
        } else if (args.length == 2 && "roulette".equalsIgnoreCase(args[0])) {
            String a = args[1].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
            if ("board".startsWith(a)) out.add("board");
            if ("display".startsWith(a)) out.add("display");
            if ("remove".startsWith(a)) out.add("remove");
            if ("stop".startsWith(a)) out.add("stop");
            if ("start".startsWith(a)) out.add("start");
        } else if (args.length == 3 && "roulette".equalsIgnoreCase(args[0]) && "display".equalsIgnoreCase(args[1])) {
            String a = args[2].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
            if ("remove".startsWith(a)) out.add("remove");
        } else if (args.length == 3 && "roulette".equalsIgnoreCase(args[0]) && "board".equalsIgnoreCase(args[1])) {
            String a = args[2].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
        } else if (args.length == 3 && "slot".equalsIgnoreCase(args[0]) && "dealer".equalsIgnoreCase(args[1])) {
            String a = args[2].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
            if ("summon".startsWith(a)) out.add("summon");
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
            if ("dealer".startsWith(a)) out.add("dealer");
        } else if (args.length == 3 && "chinchiro".equalsIgnoreCase(args[0]) && "region".equalsIgnoreCase(args[1])) {
            String a = args[2].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
        } else if (args.length == 3 && "chinchiro".equalsIgnoreCase(args[0]) && "dealer".equalsIgnoreCase(args[1])) {
            String a = args[2].toLowerCase();
            if ("set".startsWith(a)) out.add("set");
            if ("summon".startsWith(a)) out.add("summon");
        }
        return out;
    }

    private RouletteDisplayService rouletteDisplay() {
        if (plugin instanceof PeRoCasino casino && casino.getRouletteDisplayService() != null) {
            return casino.getRouletteDisplayService();
        }
        return new RouletteDisplayService(plugin);
    }
}
