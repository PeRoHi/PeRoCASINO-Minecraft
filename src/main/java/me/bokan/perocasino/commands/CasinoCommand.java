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
                sender.sendMessage("§cコンソールから使用する場合は /casino <プレイヤー名> を指定してください。");
                return true;
            }
            open(player);
            return true;
        }

        // セレクター（@p, @a等）に対応させるための処理
        List<Entity> targets;
        try {
            // 入力された引数をセレクターとして解析する
            targets = Bukkit.selectEntities(sender, args[0]);
        } catch (IllegalArgumentException e) {
            // セレクターではない（通常のプレイヤー名）場合のフォールバック
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                open(target);
            } else {
                sender.sendMessage("§cプレイヤー「§e" + args[0] + "§c」が見つかりません。");
            }
            return true;
        }

        // 解析されたターゲットのうち、プレイヤー全員にGUIを開く
        boolean opened = false;
        for (Entity entity : targets) {
            if (entity instanceof Player player) {
                open(player);
                opened = true;
            }
        }

        if (!opened) {
            sender.sendMessage("§c対象のプレイヤーが見つかりませんでした。");
        }

        return true;
    }

    public static void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);
        gui.setItem(10, createItem(Material.GOLD_INGOT, "§6§lLOAN"));
        gui.setItem(13, createItem(Material.EMERALD,    "§a§lSHOP"));
        gui.setItem(16, createItem(Material.REDSTONE,   "§c§lSABOTAGE"));
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