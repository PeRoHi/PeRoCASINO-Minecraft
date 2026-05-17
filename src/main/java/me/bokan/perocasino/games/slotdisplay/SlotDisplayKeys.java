package me.bokan.perocasino.games.slotdisplay;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * TextDisplay / Interaction に埋め込む PDC キー。
 */
public final class SlotDisplayKeys {

    private final NamespacedKey machineIdKey;
    private final NamespacedKey roleKey;

    public SlotDisplayKeys(JavaPlugin plugin) {
        this.machineIdKey = new NamespacedKey(plugin, "slot_display_machine_id");
        this.roleKey = new NamespacedKey(plugin, "slot_display_role");
    }

    public NamespacedKey machineIdKey() {
        return machineIdKey;
    }

    public NamespacedKey roleKey() {
        return roleKey;
    }

    /** TextDisplay 用: reel index 0..2 */
    public static String roleReel(int index) {
        return "reel:" + index;
    }

    public static String roleSpin() {
        return "spin";
    }

    public static String roleStop(int index) {
        return "stop:" + index;
    }
}
