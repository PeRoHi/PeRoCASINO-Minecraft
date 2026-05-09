package me.bokan.perocasino.games.slotdisplay;

import me.bokan.perocasino.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 設置型スロット（TextDisplay×3 + Interaction 操作）。
 */
public final class SlotDisplayMachine {

    private final SlotDisplayKeys keys;
    private final EconomyManager economy;

    private final String machineId;
    private final Location base;

    private final SlotStrip strip;
    private final Map<String, SlotSymbol> symbolTable;

    private final ReelState[] reels = new ReelState[3];

    private final TextDisplay[] reelDisplays = new TextDisplay[3];
    private Interaction spinInteraction;
    private final Interaction[] stopInteractions = new Interaction[3];

    private final int betDiamonds;
    private final int weightNext;
    private final int weightNextNext;
    private final String atariSymbolId;
    private final int weightNextWhenNextIsAtari;
    private final int weightNextNextWhenNextIsAtari;
    private final int payoutThree;
    private final int payoutTwo;
    private final double reelSpacing;
    private final double reelYOffset;
    private final double buttonForward;
    private final double buttonDown;

    private final ConfigurationSection machineCfg;

    /** false の間はリールは進まず固定表示（初期／ラウンド終了後） */
    private boolean spinSessionActive;

    /** 現在のラウンドでベットしたプレイヤー（払戻・ログ用） */
    private UUID roundOwner;

    public SlotDisplayMachine(SlotDisplayKeys keys,
                              EconomyManager economy,
                              String machineId,
                              Location base,
                              SlotStrip strip,
                              Map<String, SlotSymbol> symbolTable,
                              int betDiamonds,
                              int weightNext,
                              int weightNextNext,
                              String atariSymbolId,
                              int weightNextWhenNextIsAtari,
                              int weightNextNextWhenNextIsAtari,
                              int payoutThree,
                              int payoutTwo,
                              int baseStepTicks,
                              double reelSpacing,
                              double reelYOffset,
                              double buttonForward,
                              double buttonDown,
                              ConfigurationSection machineCfg) {
        this.keys = keys;
        this.economy = economy;
        this.machineId = Objects.requireNonNull(machineId, "machineId");
        this.base = Objects.requireNonNull(base, "base");
        this.strip = Objects.requireNonNull(strip, "strip");
        this.symbolTable = Map.copyOf(symbolTable);
        this.machineCfg = machineCfg;

        this.betDiamonds = Math.max(0, betDiamonds);
        this.weightNext = Math.max(0, weightNext);
        this.weightNextNext = Math.max(0, weightNextNext);
        this.atariSymbolId = atariSymbolId == null ? "" : atariSymbolId;
        this.weightNextWhenNextIsAtari = Math.max(0, weightNextWhenNextIsAtari);
        this.weightNextNextWhenNextIsAtari = Math.max(0, weightNextNextWhenNextIsAtari);
        this.payoutThree = Math.max(0, payoutThree);
        this.payoutTwo = Math.max(0, payoutTwo);

        this.reelSpacing = reelSpacing > 0 ? reelSpacing : 0.55;
        this.reelYOffset = reelYOffset;
        this.buttonForward = buttonForward > 0 ? buttonForward : 0.45;
        this.buttonDown = buttonDown;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 3; i++) {
            reels[i] = new ReelState(strip, rng.nextInt(strip.size()));
            reels[i].baseStepTicks = Math.max(1, baseStepTicks);
            reels[i].stopped = true;
        }

        spawnOrRefreshEntities();
        refreshAllDisplays();
    }

    public String machineId() {
        return machineId;
    }

    public Location baseLocation() {
        return base.clone();
    }

    public void shutdown() {
        removeEntities();
    }

    void tick() {
        if (!spinSessionActive) {
            return;
        }
        boolean anyMoving = false;
        for (int i = 0; i < 3; i++) {
            ReelState r = reels[i];
            if (!r.stopped) {
                r.tick();
                updateDisplay(i);
                anyMoving = true;
            }
        }
        if (!anyMoving && spinSessionActive) {
            settleRound();
        }
    }

    private void settleRound() {
        spinSessionActive = false;
        String[] ids = new String[3];
        for (int i = 0; i < 3; i++) {
            ids[i] = strip.at(reels[i].pos);
            reels[i].stopped = true;
        }

        boolean allEq = ids[0].equals(ids[1]) && ids[1].equals(ids[2]);
        boolean pair = !allEq && (ids[0].equals(ids[1]) || ids[1].equals(ids[2]) || ids[0].equals(ids[2]));

        int mult = 0;
        if (allEq) mult = payoutThree;
        else if (pair) mult = payoutTwo;

        int bet = betDiamonds;
        int payout = bet * mult;

        if (roundOwner != null && payout > 0) {
            economy.addWalletBalance(roundOwner, payout);
        }

        Player closer = roundOwner == null ? null : Bukkit.getPlayer(roundOwner);

        if (closer != null && closer.isOnline()) {
            if (payout > 0) {
                closer.sendMessage("§a[SLOT·設置] §f" + ids[0] + " §7| §f" + ids[1] + " §7| §f" + ids[2]
                        + " §f| 払戻 §b" + payout + " §7(財布)");
                closer.playSound(closer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
            } else {
                closer.sendMessage("§c[SLOT·設置] §f" + ids[0] + " §7| §f" + ids[1] + " §7| §f" + ids[2]
                        + " §f| はずれ");
                closer.playSound(closer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            }
        }
        roundOwner = null;
    }

    public boolean trySpin(Player player) {
        Objects.requireNonNull(player, "player");
        if (spinSessionActive) {
            player.sendMessage("§eこの台は既に回転中です。");
            return false;
        }
        if (betDiamonds > 0 && economy.getWalletBalance(player.getUniqueId()) < betDiamonds) {
            player.sendMessage("§c財布のダイヤが足りません（必要: §f" + betDiamonds + "§c）。");
            return false;
        }
        if (betDiamonds > 0) {
            economy.addWalletBalance(player.getUniqueId(), -betDiamonds);
        }

        roundOwner = player.getUniqueId();

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 3; i++) {
            reels[i].resetForSpin(rng.nextInt(strip.size()));
        }
        spinSessionActive = true;
        refreshAllDisplays();
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.4f);
        player.sendMessage("§aスピン開始！ §7各ストップでリールを止めてください。");
        return true;
    }

    public boolean tryStopReel(Player player, int reelIndex) {
        Objects.requireNonNull(player, "player");
        if (reelIndex < 0 || reelIndex > 2) return false;
        if (!spinSessionActive) {
            player.sendMessage("§e先にスピンボタンを押してください。");
            return false;
        }
        ReelState r = reels[reelIndex];
        if (r.stopped) {
            player.sendMessage("§eそのリールは既に止まっています。");
            return false;
        }
        int w1 = weightNext;
        int w2 = weightNextNext;

        if (!atariSymbolId.isBlank()) {
            String nextId = strip.at(r.pos + 1);
            if (atariSymbolId.equals(nextId)) {
                // 当たりの1つ前で押したときだけ、次20%/次次80%（比率は設定値）
                w1 = weightNextWhenNextIsAtari;
                w2 = weightNextNextWhenNextIsAtari;
            }
        }

        r.requestStop(w1, w2);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
        return true;
    }

    private void refreshAllDisplays() {
        for (int i = 0; i < 3; i++) {
            updateDisplay(i);
        }
    }

    private void updateDisplay(int reelIndex) {
        TextDisplay td = reelDisplays[reelIndex];
        if (td == null || td.isDead()) return;
        String symId = strip.at(reels[reelIndex].pos);
        SlotSymbol sym = symbolTable.get(symId);
        String glyph = sym != null ? sym.glyph() : symId;
        td.setText(glyph);
    }

    private void removeEntities() {
        for (TextDisplay td : reelDisplays) {
            if (td != null && !td.isDead()) td.remove();
        }
        if (spinInteraction != null && !spinInteraction.isDead()) spinInteraction.remove();
        for (Interaction it : stopInteractions) {
            if (it != null && !it.isDead()) it.remove();
        }
    }

    private void spawnOrRefreshEntities() {
        World world = base.getWorld();
        if (world == null) return;

        removeEntities();

        double yawRad = Math.toRadians(base.getYaw());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        Vector forward = new Vector(fx, 0, fz).normalize();
        Vector right = new Vector(fz, 0, -fx).normalize();

        double centerY = base.getY() + reelYOffset;

        for (int i = 0; i < 3; i++) {
            double ox = (i - 1) * reelSpacing;
            Location loc = base.clone().add(right.clone().multiply(ox));
            loc.setY(centerY);
            TextDisplay td = world.spawn(loc, TextDisplay.class);
            td.setBillboard(Display.Billboard.CENTER);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setSeeThrough(true);
            td.setShadowed(true);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            td.setPersistent(false);
            td.setInvulnerable(true);
            tagEntity(td, SlotDisplayKeys.roleReel(i));
            reelDisplays[i] = td;
        }

        Location spinLoc = base.clone().add(forward.clone().multiply(buttonForward)).add(0, buttonDown, 0);
        spinInteraction = world.spawn(spinLoc, Interaction.class);
        spinInteraction.setInteractionWidth(1.0f);
        spinInteraction.setInteractionHeight(0.45f);
        spinInteraction.setResponsive(true);
        spinInteraction.setPersistent(false);
        spinInteraction.setInvulnerable(true);
        tagEntity(spinInteraction, SlotDisplayKeys.roleSpin());

        for (int i = 0; i < 3; i++) {
            double ox = (i - 1) * reelSpacing;
            Location loc = base.clone().add(right.clone().multiply(ox)).add(forward.clone().multiply(buttonForward * 0.85))
                    .add(0, buttonDown + 0.05, 0);
            Interaction it = world.spawn(loc, Interaction.class);
            it.setInteractionWidth(0.65f);
            it.setInteractionHeight(0.35f);
            it.setResponsive(true);
            it.setPersistent(false);
            it.setInvulnerable(true);
            tagEntity(it, SlotDisplayKeys.roleStop(i));
            stopInteractions[i] = it;
        }

        // machineCfg は将来の個別上書き用（現状未使用）
        if (machineCfg != null) {
            // no-op
        }
    }

    private void tagEntity(Entity entity, String role) {
        entity.getPersistentDataContainer().set(keys.machineIdKey(), PersistentDataType.STRING, machineId);
        entity.getPersistentDataContainer().set(keys.roleKey(), PersistentDataType.STRING, role);
    }

    /**
     * エンティティが無効なら作り直す。
     */
    void ensureEntities() {
        boolean missing = spinInteraction == null || spinInteraction.isDead();
        for (TextDisplay td : reelDisplays) {
            if (td == null || td.isDead()) missing = true;
        }
        for (Interaction it : stopInteractions) {
            if (it == null || it.isDead()) missing = true;
        }
        if (missing) {
            spawnOrRefreshEntities();
            refreshAllDisplays();
        }
    }

    boolean isSpinSessionActive() {
        return spinSessionActive;
    }
}
