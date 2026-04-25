package me.bokan.perocasino.roulette;

import me.bokan.perocasino.economy.EconomyManager;
import me.bokan.perocasino.listeners.RouletteBetMenuListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ルーレット拠点の常時ループ（ベット→回転→クールダウン）と、近傍プレイヤー向けBossBar表示。
 */
public class RouletteHubService extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final RouletteBetMenuListener betMenuListener;

    private final BossBar bossBar;

    private Location hub;
    private double radius;
    private int betTicks;
    private int spinTicks;
    private int cooldownTicks;

    private RoulettePhase phase = RoulettePhase.BETTING;
    private int phaseTicksRemaining;

    private final List<Material> symbolPool = new ArrayList<>();

    private RouletteAngleConfig angleConfig;
    private RouletteDisplayService displayService;

    // SPINNING開始時に「停止角度」を先に決め、精算と表示の停止を同期する
    private RouletteSettlement.AngleRoundResult pendingResult;

    public RouletteHubService(JavaPlugin plugin,
                              EconomyManager economyManager,
                              RouletteBetMenuListener betMenuListener,
                              RouletteDisplayService displayService) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.betMenuListener = betMenuListener;

        this.bossBar = Bukkit.createBossBar("§7PeRo Roulette", BarColor.PURPLE, BarStyle.SOLID);
        this.bossBar.setVisible(false);
        this.bossBar.setProgress(1.0);

        this.displayService = (displayService == null) ? new RouletteDisplayService(plugin) : displayService;
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        FileConfiguration cfg = plugin.getConfig();
        String worldName = cfg.getString("roulette.world", "");
        int x = cfg.getInt("roulette.x", 0);
        int y = cfg.getInt("roulette.y", 0);
        int z = cfg.getInt("roulette.z", 0);

        World world = worldName == null || worldName.isBlank() ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            hub = null;
        } else {
            hub = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        }

        radius = cfg.getDouble("roulette.notify-radius", 40.0);
        betTicks = Math.max(20, cfg.getInt("roulette.bet-seconds", 20) * 20);
        spinTicks = Math.max(20, cfg.getInt("roulette.spin-seconds", 3) * 20);
        cooldownTicks = Math.max(20, cfg.getInt("roulette.cooldown-seconds", 5) * 20);

        // 角度セグメント設定（画像制作・当たり判定の基準）
        try {
            angleConfig = RouletteAngleConfig.loadAndValidate(cfg);
            plugin.getLogger().info("[Roulette] angle segments loaded: total=" + angleConfig.totalDegrees()
                    + "°, segments=" + angleConfig.segments().size());
        } catch (Exception e) {
            angleConfig = null;
            plugin.getLogger().warning("[Roulette] angle segments invalid: " + e.getMessage());
        }

        // 表示（ItemDisplay）
        displayService.reloadFromConfig();

        symbolPool.clear();
        List<String> raw = cfg.getStringList("slot-machine.symbols");
        if (raw == null || raw.isEmpty()) {
            symbolPool.addAll(List.of(
                    Material.COAL_ORE,
                    Material.COPPER_ORE,
                    Material.IRON_ORE,
                    Material.GOLD_ORE,
                    Material.REDSTONE_ORE,
                    Material.LAPIS_ORE,
                    Material.EMERALD_ORE,
                    Material.DIAMOND_ORE
            ));
        } else {
            for (String s : raw) {
                try {
                    symbolPool.add(Material.valueOf(s.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("無効なルーレットシンボル: " + s);
                }
            }
            if (symbolPool.isEmpty()) {
                symbolPool.add(Material.DIAMOND_ORE);
            }
        }

        // フェーズをリセット
        phase = RoulettePhase.BETTING;
        phaseTicksRemaining = betTicks;
        pendingResult = null;
        RouletteBetMenuListener.setHubPhase(phase);
        updateBossBarForPhase();
    }

    @Override
    public void run() {
        if (hub == null || hub.getWorld() == null) {
            bossBar.setVisible(false);
            for (Player p : new ArrayList<>(bossBar.getPlayers())) {
                bossBar.removePlayer(p);
            }
            return;
        }

        syncBossBarPlayers();

        phaseTicksRemaining--;
        if (phaseTicksRemaining < 0) {
            advancePhase();
        }
        updateBossBarProgress();
    }

    private void advancePhase() {
        switch (phase) {
            case BETTING -> {
                phase = RoulettePhase.SPINNING;
                phaseTicksRemaining = spinTicks;
                if (angleConfig != null) {
                    pendingResult = RouletteSettlement.randomAngleResult(angleConfig);
                    displayService.startSpinning();
                } else {
                    pendingResult = null;
                }
            }
            case SPINNING -> {
                if (angleConfig == null || pendingResult == null) {
                    plugin.getLogger().warning("[Roulette] angleConfig/pendingResult is null; skipping settlement.");
                } else {
                    displayService.stopAtAngle(pendingResult.stopAngleDeg());
                    RouletteSettlement.settleRound(
                            economyManager,
                            betMenuListener,
                            pendingResult,
                            hub,
                            radius
                    );
                }
                phase = RoulettePhase.COOLDOWN;
                phaseTicksRemaining = cooldownTicks;
            }
            case COOLDOWN -> {
                phase = RoulettePhase.BETTING;
                phaseTicksRemaining = betTicks;
                pendingResult = null;
            }
        }
        RouletteBetMenuListener.setHubPhase(phase);
        updateBossBarForPhase();
    }

    private void syncBossBarPlayers() {
        World w = hub.getWorld();
        double r2 = radius * radius;

        // 近傍にいるプレイヤーを追加
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(w)) continue;
            if (p.getLocation().distanceSquared(hub) <= r2) {
                if (!bossBar.getPlayers().contains(p)) {
                    bossBar.addPlayer(p);
                }
            } else {
                if (bossBar.getPlayers().contains(p)) {
                    bossBar.removePlayer(p);
                }
            }
        }

        bossBar.setVisible(!bossBar.getPlayers().isEmpty());
    }

    private void updateBossBarForPhase() {
        switch (phase) {
            case BETTING -> {
                bossBar.setColor(BarColor.GREEN);
            }
            case SPINNING -> {
                bossBar.setColor(BarColor.YELLOW);
            }
            case COOLDOWN -> {
                bossBar.setColor(BarColor.BLUE);
            }
        }
        updateBossBarTitleAndProgress();
    }

    private void updateBossBarTitleAndProgress() {
        int total = switch (phase) {
            case BETTING -> betTicks;
            case SPINNING -> spinTicks;
            case COOLDOWN -> cooldownTicks;
        };

        int sec = Math.max(0, (phaseTicksRemaining + 19) / 20);
        String title = switch (phase) {
            case BETTING -> "§aルーレット: ベット受付 §f" + sec + "s";
            case SPINNING -> "§eルーレット: 抽選中… §f" + sec + "s";
            case COOLDOWN -> "§bルーレット: クールダウン §f" + sec + "s";
        };
        bossBar.setTitle(title);

        if (total <= 0) {
            bossBar.setProgress(1.0);
        } else {
            double prog = Math.max(0.0, Math.min(1.0, (double) Math.max(0, phaseTicksRemaining) / (double) total));
            bossBar.setProgress(prog);
        }
    }

    private void updateBossBarProgress() {
        updateBossBarTitleAndProgress();
    }

    public void shutdown() {
        cancel();
        for (Player p : new ArrayList<>(bossBar.getPlayers())) {
            bossBar.removePlayer(p);
        }
        bossBar.removeAll();
    }
}
