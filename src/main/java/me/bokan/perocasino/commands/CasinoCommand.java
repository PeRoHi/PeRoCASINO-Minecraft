package me.bokan.perocasino.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * /casino [playerName]
 *
 * - 引数なし + プレイヤー実行 → 自身のカジノGUIを開く
 * - 引数あり           → 指定プレイヤーのGUIを開く（コンソール・コマブロ対応）
 */
public class CasinoCommand implements CommandExecutor {

    /** GUIタイトル。CasinoMenuListener と一致させること。 */
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

        // プレイヤー名が指定された場合（コンソール・コマブロ・他プレイヤー共通）
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cプレイヤー「§e" + args[0] + "§c」が見つかりません。");
            return true;
        }
        open(target);
        return true;
    }

    /** どこからでも呼べるメインメニューオープナー。 */
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
