package me.bokan.perocasino.games.slotdisplay;

import me.bokan.perocasino.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

    /** 左・中・右リールそれぞれのストリップ（通常は a/b/c の 10 コマ） */
    private final SlotStrip[] reelStrips;
    private final Map<String, SlotSymbol> symbolTable;

    private final ReelState[] reels = new ReelState[3];

    private final TextDisplay[] reelDisplays = new TextDisplay[3];
    private Interaction spinInteraction;
    private final Interaction[] stopInteractions = new Interaction[3];

    private final int betDiamonds;
    private final int weightNext;
    private final int weightNextNext;
    private final List<String> atariSymbolIds;
    private final int weightNextWhenNextIsAtari;
    private final int weightNextNextWhenNextIsAtari;
    /** 当たりマーク3つの倍率（賭け金×この値を財布へ） */
    private final int payoutTripleAtari;
    /** 同一マーク（match-group または同一ID）3つの倍率 */
    private final int payoutTripleSameMark;
    private final double reelSpacing;
    private final double reelYOffset;
    private final double buttonForward;
    private final double buttonDown;

    private final ConfigurationSection machineCfg;

    /** false の間はリールは進まず固定表示（初期／ラウンド終了後） */
    private boolean spinSessionActive;

    /** 現在のラウンドでベットしたプレイヤー（払戻・ログ用） */
    private UUID roundOwner;

    /** ボタン消失演出用：このラウンドで消したブロックの復元情報 */
    private final Map<String, BlockData> removedButtons = new HashMap<>();

    public SlotDisplayMachine(SlotDisplayKeys keys,
                              EconomyManager economy,
                              String machineId,
                              Location base,
                              SlotStrip[] reelStrips,
                              Map<String, SlotSymbol> symbolTable,
                              int betDiamonds,
                              int weightNext,
                              int weightNextNext,
                              List<String> atariSymbolIds,
                              int weightNextWhenNextIsAtari,
                              int weightNextNextWhenNextIsAtari,
                              int payoutTripleAtari,
                              int payoutTripleSameMark,
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
        Objects.requireNonNull(reelStrips, "reelStrips");
        if (reelStrips.length != 3) {
            throw new IllegalArgumentException("reelStrips must have length 3");
        }
        this.reelStrips = Arrays.copyOf(reelStrips, 3);
        this.symbolTable = Map.copyOf(symbolTable);
        this.machineCfg = machineCfg;

        this.betDiamonds = Math.max(0, betDiamonds);
        this.weightNext = Math.max(0, weightNext);
        this.weightNextNext = Math.max(0, weightNextNext);
        this.atariSymbolIds = atariSymbolIds == null ? List.of() : List.copyOf(atariSymbolIds);
        this.weightNextWhenNextIsAtari = Math.max(0, weightNextWhenNextIsAtari);
        this.weightNextNextWhenNextIsAtari = Math.max(0, weightNextNextWhenNextIsAtari);
        this.payoutTripleAtari = Math.max(0, payoutTripleAtari);
        this.payoutTripleSameMark = Math.max(0, payoutTripleSameMark);

        this.reelSpacing = reelSpacing > 0 ? reelSpacing : 0.55;
        this.reelYOffset = reelYOffset;
        this.buttonForward = buttonForward > 0 ? buttonForward : 0.45;
        this.buttonDown = buttonDown;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 3; i++) {
            SlotStrip rs = this.reelStrips[i];
            reels[i] = new ReelState(rs, rng.nextInt(rs.size()));
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

    /** 役判定用キー（当たり／ハズレ／グループ／同一ID）。 */
    private static String outcomeKey(SlotSymbol sym, String id) {
        if (sym == null) {
            return "ID:" + id;
        }
        if (sym.hazure()) {
            return "HAZURE";
        }
        if (sym.winning()) {
            return "ATARI";
        }
        if (sym.matchGroup() != null) {
            return "G:" + sym.matchGroup();
        }
        return "ID:" + id;
    }

    private void settleRound() {
        spinSessionActive = false;
        String[] ids = new String[3];
        for (int i = 0; i < 3; i++) {
            ids[i] = reelStrips[i].at(reels[i].pos);
            reels[i].stopped = true;
        }

        UUID owner = roundOwner;
        int bet = betDiamonds;

        String k0 = outcomeKey(symbolTable.get(ids[0]), ids[0]);
        String k1 = outcomeKey(symbolTable.get(ids[1]), ids[1]);
        String k2 = outcomeKey(symbolTable.get(ids[2]), ids[2]);

        boolean triple = k0.equals(k1) && k1.equals(k2);
        boolean pair = !triple && (k0.equals(k1) || k1.equals(k2) || k0.equals(k2));

        Player closer = owner == null ? null : Bukkit.getPlayer(owner);

        if (owner != null && bet > 0) {
            if (triple) {
                if ("ATARI".equals(k0)) {
                    int payout = bet * payoutTripleAtari;
                    economy.addWalletBalance(owner, payout);
                    if (closer != null && closer.isOnline()) {
                        closer.sendMessage("§a[SLOT·設置] §f" + ids[0] + " §7| §f" + ids[1] + " §7| §f" + ids[2]
                                + " §f| 当たり3つ §b×" + payoutTripleAtari + " §a→ §b" + payout + " §7(財布)");
                        closer.playSound(closer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
                    }
                } else if ("HAZURE".equals(k0)) {
                    if (closer != null && closer.isOnline()) {
                        economy.takeBetFromWalletOrDebt(closer, bet);
                    } else {
                        economy.takeBetFromWalletOrDebt(owner, bet);
                    }
                    if (closer != null && closer.isOnline()) {
                        closer.sendMessage("§c[SLOT·設置] §f" + ids[0] + " §7| §f" + ids[1] + " §7| §f" + ids[2]
                                + " §c| ハズレ3つ §7追加没収 §c" + bet + " §7(財布・借金)");
                        closer.playSound(closer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
                    }
                } else {
                    int payout = bet * payoutTripleSameMark;
                    economy.addWalletBalance(owner, payout);
                    if (closer != null && closer.isOnline()) {
                        closer.sendMessage("§a[SLOT·設置] §f" + ids[0] + " §7| §f" + ids[1] + " §7| §f" + ids[2]
                                + " §f| 同一マーク3つ §b×" + payoutTripleSameMark + " §a→ §b" + payout + " §7(財布)");
                        closer.playSound(closer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
                    }
                }
            } else if (pair) {
                economy.addWalletBalance(owner, bet);
                if (closer != null && closer.isOnline()) {
                    closer.sendMessage("§e[SLOT·設置] §f" + ids[0] + " §7| §f" + ids[1] + " §7| §f" + ids[2]
                            + " §f| 同じマーク2つ §a賭け金返金 §b" + bet);
                    closer.playSound(closer.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.3f);
                }
            } else {
                if (closer != null && closer.isOnline()) {
                    closer.sendMessage("§c[SLOT·設置] §f" + ids[0] + " §7| §f" + ids[1] + " §7| §f" + ids[2]
                            + " §7| バラバラ §c賭け金没収（スピン時に支払済み）");
                    closer.playSound(closer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                }
            }
        }

        roundOwner = null;
        restoreRemovedButtons();
    }

    public boolean trySpin(Player player) {
        Objects.requireNonNull(player, "player");
        if (spinSessionActive) {
            player.sendMessage("§eこの台は既に回転中です。");
            return false;
        }
        if (betDiamonds > 0) {
            economy.takeBetFromWalletOrDebt(player, betDiamonds);
        }

        roundOwner = player.getUniqueId();

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 3; i++) {
            reels[i].resetForSpin(rng.nextInt(reelStrips[i].size()));
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

        SlotStrip st = reelStrips[reelIndex];
        String nextId = st.at(r.pos + 1);
        if (!atariSymbolIds.isEmpty()) {
            for (String aid : atariSymbolIds) {
                if (aid != null && aid.equals(nextId)) {
                    w1 = weightNextWhenNextIsAtari;
                    w2 = weightNextNextWhenNextIsAtari;
                    break;
                }
            }
        }

        r.requestStop(w1, w2);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
        return true;
    }

    /**
     * ブロックの「ボタン押下」を役割に解決する。
     * 位置は base（中心）と yaw（方角）から、カードinal（N/E/S/W）向きに丸めて計算する。
     *
     * @return role。spin / stop:0..2 / null（該当なし）
     */
    public String resolveBlockButtonRole(Block clicked) {
        if (clicked == null) return null;
        if (base.getWorld() == null || clicked.getWorld() != base.getWorld()) return null;

        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();

        BlockFace facing = yawToFacing(base.getYaw());
        BlockFace right = rotateRight(facing);

        // ボタンは「スロット正面（facing 方向）に1マス」「リール高さの1マス下」をデフォルトとする。
        // - stop: 左/中/右それぞれのボタン（3つ）
        // - spin: 中央ボタンよりさらに前に1マス（合計2マス前）に置く
        int stopForward = 1;
        int stopDown = 1;
        int spinForward = 2;
        int spinDown = 1;

        // stop buttons
        for (int i = 0; i < 3; i++) {
            int ox = i - 1; // -1,0,+1
            int rx = bx + right.getModX() * ox + facing.getModX() * stopForward;
            int rz = bz + right.getModZ() * ox + facing.getModZ() * stopForward;
            int ry = by + (int) Math.floor(reelYOffset) - stopDown;
            if (clicked.getX() == rx && clicked.getY() == ry && clicked.getZ() == rz) {
                return SlotDisplayKeys.roleStop(i);
            }
        }

        // spin button (center)
        int sx = bx + facing.getModX() * spinForward;
        int sz = bz + facing.getModZ() * spinForward;
        int sy = by + (int) Math.floor(reelYOffset) - spinDown;
        if (clicked.getX() == sx && clicked.getY() == sy && clicked.getZ() == sz) {
            return SlotDisplayKeys.roleSpin();
        }

        return null;
    }

    /**
     * 押下したボタンを「その瞬間だけ」消す。
     * ラウンド終了時に {@link #restoreRemovedButtons()} で復元する。
     */
    public void removeButtonBlockForThisRound(Block buttonBlock) {
        if (buttonBlock == null) return;
        if (!spinSessionActive) {
            // spin しない限り復元ポイントが来ないので、未回転時は消さない（誤爆防止）
            return;
        }
        if (!isButtonMaterial(buttonBlock.getType())) return;
        String key = blockKey(buttonBlock);
        removedButtons.putIfAbsent(key, buttonBlock.getBlockData().clone());
        buttonBlock.setType(Material.AIR, false);
    }

    private void restoreRemovedButtons() {
        if (removedButtons.isEmpty()) return;
        World world = base.getWorld();
        if (world == null) {
            removedButtons.clear();
            return;
        }
        for (Map.Entry<String, BlockData> e : removedButtons.entrySet()) {
            Block b = blockFromKey(world, e.getKey());
            if (b == null) continue;
            if (b.getType() == Material.AIR) {
                // ブロックデータから Material を復元する（AIR になることは想定しない）
                BlockData data = e.getValue();
                b.setType(data.getMaterial(), false);
                b.setBlockData(data, false);
            }
        }
        removedButtons.clear();
    }

    private static boolean isButtonMaterial(Material m) {
        if (m == null) return false;
        String name = m.name();
        return name.endsWith("_BUTTON");
    }

    private static String blockKey(Block b) {
        return b.getX() + "," + b.getY() + "," + b.getZ();
    }

    private static Block blockFromKey(World world, String key) {
        if (world == null || key == null) return null;
        String[] p = key.split(",", 3);
        if (p.length != 3) return null;
        try {
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int z = Integer.parseInt(p[2]);
            return world.getBlockAt(x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BlockFace yawToFacing(float yaw) {
        // Bukkit yaw: 0 = South, 90 = West, 180 = North, -90/270 = East
        float rot = (yaw % 360 + 360) % 360;
        if (rot >= 45 && rot < 135) return BlockFace.WEST;
        if (rot >= 135 && rot < 225) return BlockFace.NORTH;
        if (rot >= 225 && rot < 315) return BlockFace.EAST;
        return BlockFace.SOUTH;
    }

    private static BlockFace rotateRight(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    private void refreshAllDisplays() {
        for (int i = 0; i < 3; i++) {
            updateDisplay(i);
        }
    }

    private void updateDisplay(int reelIndex) {
        TextDisplay td = reelDisplays[reelIndex];
        if (td == null || td.isDead()) return;
        String symId = reelStrips[reelIndex].at(reels[reelIndex].pos);
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
