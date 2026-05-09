package me.bokan.perocasino.games.slotdisplay;

import me.bokan.perocasino.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 設置型スロット（TextDisplay）の読み込みと常時 tick。
 */
public final class SlotDisplayService {

    private final JavaPlugin plugin;
    private final EconomyManager economy;
    private final SlotDisplayKeys keys;

    private final Map<String, SlotDisplayMachine> machines = new ConcurrentHashMap<>();
    private BukkitTask task;

    public SlotDisplayService(JavaPlugin plugin, EconomyManager economy) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.keys = new SlotDisplayKeys(plugin);
    }

    public SlotDisplayKeys keys() {
        return keys;
    }

    public SlotDisplayMachine getMachine(String machineId) {
        if (machineId == null) return null;
        return machines.get(machineId);
    }

    public Map<String, SlotDisplayMachine> machinesView() {
        return Collections.unmodifiableMap(machines);
    }

    public void reloadFromConfig() {
        FileConfiguration cfg = plugin.getConfig();
        boolean enabled = cfg.getBoolean("slot-display.enabled", false);

        for (SlotDisplayMachine m : machines.values()) {
            m.shutdown();
        }
        machines.clear();

        if (task != null) {
            task.cancel();
            task = null;
        }

        if (!enabled) {
            return;
        }

        Map<String, SlotSymbol> symbolTable = loadSymbols(cfg.getConfigurationSection("slot-display.symbols"));
        if (symbolTable.isEmpty()) {
            plugin.getLogger().warning("slot-display.symbols が空です。設置スロットは無効です。");
            return;
        }

        SlotStrip[] reelStrips = loadReelStrips(cfg, symbolTable, plugin);
        if (reelStrips == null) {
            List<String> fallback = cfg.getStringList("slot-display.strip");
            if (fallback == null || fallback.isEmpty()) {
                plugin.getLogger().warning("slot-display.reel-strips（または旧 strip）が未定義です。");
                return;
            }
            for (String id : fallback) {
                if (!symbolTable.containsKey(id)) {
                    plugin.getLogger().warning("strip に未定義のシンボルがあります: " + id);
                }
            }
            SlotStrip one = new SlotStrip(fallback);
            reelStrips = new SlotStrip[]{one, one, one};
        }

        List<String> atariIds = cfg.getStringList("slot-display.atari-symbol-ids");
        if (atariIds == null || atariIds.isEmpty()) {
            String legacy = cfg.getString("slot-display.atari-symbol-id", "");
            if (legacy != null && !legacy.isBlank()) {
                atariIds = List.of(legacy.trim());
            } else {
                atariIds = List.of();
            }
        }

        int bet = Math.max(0, cfg.getInt("slot-display.bet-diamonds", 1));
        int wNext = Math.max(0, cfg.getInt("slot-display.stop-distance-weights.next", 80));
        int wNext2 = Math.max(0, cfg.getInt("slot-display.stop-distance-weights.next-next", 20));
        int wNextAtariPrev = Math.max(0, cfg.getInt("slot-display.stop-distance-weights.when-next-is-atari.next", 20));
        int wNext2AtariPrev = Math.max(0, cfg.getInt("slot-display.stop-distance-weights.when-next-is-atari.next-next", 80));
        int payoutThree = Math.max(0, cfg.getInt("slot-display.payouts.three-of-a-kind", 8));
        int payoutTwo = Math.max(0, cfg.getInt("slot-display.payouts.two-of-a-kind", 2));
        int baseStepTicks = Math.max(1, cfg.getInt("slot-display.reel-step-base-ticks", 2));
        double reelSpacing = cfg.getDouble("slot-display.layout.reel-spacing", 0.55);
        double reelYOffset = cfg.getDouble("slot-display.layout.reel-y-offset", 1.15);
        double buttonForward = cfg.getDouble("slot-display.layout.button-forward", 0.45);
        double buttonDown = cfg.getDouble("slot-display.layout.button-down", -0.35);

        ConfigurationSection rootMachines = cfg.getConfigurationSection("slot-display.machines");
        if (rootMachines == null) {
            return;
        }

        for (String machineId : rootMachines.getKeys(false)) {
            ConfigurationSection msec = rootMachines.getConfigurationSection(machineId);
            if (msec == null) continue;

            String worldName = msec.getString("world", "");
            World world = worldName == null || worldName.isBlank() ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("スロット台 " + machineId + " のワールドが見つかりません: " + worldName);
                continue;
            }

            double bx = msec.getDouble("x");
            double by = msec.getDouble("y");
            double bz = msec.getDouble("z");
            float yaw = (float) msec.getDouble("yaw", 0.0);
            float pitch = (float) msec.getDouble("pitch", 0.0);

            Location base = new Location(world, bx + 0.5, by, bz + 0.5, yaw, pitch);

            double spacing = msec.contains("reel-spacing") ? msec.getDouble("reel-spacing") : reelSpacing;
            double ryOff = msec.contains("reel-y-offset") ? msec.getDouble("reel-y-offset") : reelYOffset;
            double btnFwd = msec.contains("button-forward") ? msec.getDouble("button-forward") : buttonForward;
            double btnDown = msec.contains("button-down") ? msec.getDouble("button-down") : buttonDown;

            String canonicalId = machineId.toLowerCase(Locale.ROOT);

            SlotDisplayMachine machine = new SlotDisplayMachine(
                    keys,
                    economy,
                    canonicalId,
                    base,
                    reelStrips,
                    symbolTable,
                    bet,
                    wNext,
                    wNext2,
                    atariIds,
                    wNextAtariPrev,
                    wNext2AtariPrev,
                    payoutThree,
                    payoutTwo,
                    baseStepTicks,
                    spacing,
                    ryOff,
                    btnFwd,
                    btnDown,
                    msec
            );
            machines.put(canonicalId, machine);
        }

        long period = Math.max(1L, cfg.getLong("slot-display.tick-period", 1L));
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (SlotDisplayMachine m : machines.values()) {
                    m.ensureEntities();
                    m.tick();
                }
            }
        }.runTaskTimer(plugin, 1L, period);
    }

    private static Map<String, SlotSymbol> loadSymbols(ConfigurationSection symbolsRoot) {
        if (symbolsRoot == null) return Map.of();
        Map<String, SlotSymbol> out = new HashMap<>();
        for (String id : symbolsRoot.getKeys(false)) {
            ConfigurationSection one = symbolsRoot.getConfigurationSection(id);
            if (one == null) continue;
            String glyph = one.getString("glyph", id);
            int weight = Math.max(0, one.getInt("weight", 1));
            boolean winning = one.getBoolean("winning", false);
            out.put(id, new SlotSymbol(id, glyph, weight, winning));
        }
        return out;
    }

    /**
     * 左・中・右それぞれのストリップ（YAML のリストのリストで 3 本）。
     */
    private static SlotStrip[] loadReelStrips(FileConfiguration cfg, Map<String, SlotSymbol> symbols, JavaPlugin plugin) {
        List<?> root = cfg.getList("slot-display.reel-strips");
        if (root == null || root.size() != 3) {
            return null;
        }
        SlotStrip[] out = new SlotStrip[3];
        for (int i = 0; i < 3; i++) {
            Object rowObj = root.get(i);
            if (!(rowObj instanceof List<?> row)) {
                return null;
            }
            List<String> ids = new ArrayList<>();
            for (Object o : row) {
                if (o != null) {
                    ids.add(o.toString());
                }
            }
            if (ids.isEmpty()) {
                return null;
            }
            out[i] = new SlotStrip(ids);
            for (String sid : ids) {
                if (!symbols.containsKey(sid)) {
                    plugin.getLogger().warning("reel-strips に未定義シンボル: " + sid);
                }
            }
        }
        return out;
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (SlotDisplayMachine m : machines.values()) {
            m.shutdown();
        }
        machines.clear();
    }
}
