package me.bokan.perocasino.commandwand;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** config の {@code command-wand} 設定に基づくコマンド杖アイテムの生成。 */
public final class CommandWandItems {

    private CommandWandItems() {
    }

    public static ItemStack createWand(FileConfiguration cfg, String plainDisplayName) {
        Material mat = wandMaterial(cfg);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6" + plainDisplayName);
            if (cfg.getBoolean("command-wand.match-custom-model-data", false)) {
                meta.setCustomModelData(cfg.getInt("command-wand.custom-model-data", 0));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** {@code command-wand.wands} に登録されている表示名ごとに杖を1本ずつ生成（設定順）。 */
    public static List<ItemStack> allConfiguredWands(FileConfiguration cfg) {
        List<ItemStack> out = new ArrayList<>();
        ConfigurationSection wands = cfg.getConfigurationSection("command-wand.wands");
        if (wands == null) {
            return out;
        }
        for (String key : wands.getKeys(false)) {
            out.add(createWand(cfg, key));
        }
        return out;
    }

    public static boolean matches(ItemStack item, FileConfiguration cfg) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (item.getType() != wandMaterial(cfg)) {
            return false;
        }
        if (!cfg.getBoolean("command-wand.match-custom-model-data", false)) {
            return true;
        }
        if (!item.hasItemMeta() || !item.getItemMeta().hasCustomModelData()) {
            return false;
        }
        return item.getItemMeta().getCustomModelData() == cfg.getInt("command-wand.custom-model-data", 0);
    }

    private static Material wandMaterial(FileConfiguration cfg) {
        String matName = cfg.getString("command-wand.material", "CARROT_ON_A_STICK");
        try {
            return Material.valueOf(matName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Material.CARROT_ON_A_STICK;
        }
    }
}
