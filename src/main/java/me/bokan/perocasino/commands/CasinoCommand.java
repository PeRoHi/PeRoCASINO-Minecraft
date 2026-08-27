package me.bokan.perocasino.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.List;

public class CasinoCommand implements CommandExecutor {

    public static final String GUI_TITLE = "§0§lPeRo Casino";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§c使用法: /casino <プレイヤー名>");
                return true;
            }
            open(player);
            return true;
        }

        // セレクター（@p, @a等）を解決する処理
        List<Entity> targets;
        try {
            targets = Bukkit.selectEntities(sender, args[0]);
        } catch (IllegalArgumentException e) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("§cプレイヤー「§e" + args[0] + "§c」が見つかりません。");
                return true;
            }
            if (!canOpenFor(sender, target)) {
                sender.sendMessage("§c他プレイヤーのメニューを開く権限がありません。");
                return true;
            }
            open(target);
            return true;
        }

        boolean opened = false;
        boolean denied = false;
        for (Entity entity : targets) {
            if (entity instanceof Player player) {
                if (!canOpenFor(sender, player)) {
                    denied = true;
                    continue;
                }
                open(player);
                opened = true;
            }
        }

        if (!opened) {
            if (denied) {
                sender.sendMessage("§c他プレイヤーのメニューを開く権限がありません。");
            } else {
                sender.sendMessage("§c対象のプレイヤーが見つかりませんでした。");
            }
        }

        return true;
    }

    private static boolean canOpenFor(CommandSender sender, Player target) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (player.getUniqueId().equals(target.getUniqueId())) {
            return true;
        }
        return player.hasPermission("perocasino.admin");
    }

    public static void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);
        gui.setItem(10, createItem(Material.GOLD_INGOT, "§6§lLOAN"));
        gui.setItem(13, createItem(Material.EMERALD,    "§a§lSHOP"));
        gui.setItem(16, createItem(Material.REDSTONE,   "§c§lSABOTAGE"));
        
        // 【追加】ルーレットへの入り口ボタン
        gui.setItem(22, createItem(Material.ENDER_CHEST, "§d§lROULETTE"));

        // 追加ゲーム（骨組み含む）
        gui.setItem(19, createItem(Material.TRIPWIRE_HOOK, "§e§lSLOT"));
        gui.setItem(25, createItem(Material.TOTEM_OF_UNDYING, "§5§lHI-LO (WIP)"));
        gui.setItem(28, createItem(Material.NAME_TAG, "§2§lBLACKJACK (WIP)"));
        
        gui.setItem(49, createItem(Material.BARRIER,    "§7[閉じる]"));
        player.openInventory(gui);
    }

    static ItemStack createItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        item.setItemMeta(meta);
        return item;
    }
}