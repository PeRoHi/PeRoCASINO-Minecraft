package me.bokan.perocasino.games.chinchiro;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * チンチロ用サイコロ表示（{@link ItemDisplay}）。設定された AABB 内に 3 個を重ならず配置する。
 */
public final class ChinchiroDiceService {

    private static final int DEFAULT_CMD = 9020;

    private final JavaPlugin plugin;

    private boolean enabled = true;
    private int customModelData = DEFAULT_CMD;
    /** ワールドでの立方体モデルの辺の長さ（ブロック）。dice.json は 16³＝モデルとして 1 を想定する。ItemDisplay で NONE と併せるための実寸。 */
    private float edgeLengthBlocks = 0.5f;
    /** Paper/MC 1.21系の ItemDisplay で見た目だけ小さくなる場合の描画補正。配置判定には使わない。 */
    private float modelScaleCorrection = 10.0f;
    /** XZ 平面上の中心同士の最低距離（ブロック） */
    private double separation = 0.55;
    private int randomTries = 120;

    private String regionWorld = "";
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    private final List<UUID> displayUuids = new ArrayList<>();

    public ChinchiroDiceService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reloadFromConfig() {
        removeAllDisplays();
        FileConfiguration cfg = plugin.getConfig();
        enabled = cfg.getBoolean("chinchiro.enabled", true);
        customModelData = cfg.getInt("chinchiro.dice.custom-model-data", DEFAULT_CMD);
        if (cfg.isSet("chinchiro.dice.edge-length-blocks")) {
            edgeLengthBlocks = clampEdge((float) cfg.getDouble("chinchiro.dice.edge-length-blocks", 0.5));
        } else {
            edgeLengthBlocks = clampEdge((float) cfg.getDouble("chinchiro.dice.display-scale", 0.5));
        }
        modelScaleCorrection = clampCorrection((float) cfg.getDouble("chinchiro.dice.model-scale-correction", 10.0));
        separation = cfg.getDouble("chinchiro.dice.separation", 0.55);
        randomTries = Math.max(20, cfg.getInt("chinchiro.dice.random-tries", 120));

        regionWorld = cfg.getString("chinchiro.dice.region.world", "");
        minX = cfg.getInt("chinchiro.dice.region.min.x", 0);
        minY = cfg.getInt("chinchiro.dice.region.min.y", 0);
        minZ = cfg.getInt("chinchiro.dice.region.min.z", 0);
        maxX = cfg.getInt("chinchiro.dice.region.max.x", 0);
        maxY = cfg.getInt("chinchiro.dice.region.max.y", 0);
        maxZ = cfg.getInt("chinchiro.dice.region.max.z", 0);
    }

    public void removeAllDisplays() {
        for (UUID id : new ArrayList<>(displayUuids)) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        displayUuids.clear();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasValidRegion() {
        if (regionWorld == null || regionWorld.isBlank()) {
            return false;
        }
        World w = Bukkit.getWorld(regionWorld);
        if (w == null) {
            return false;
        }
        int loX = Math.min(minX, maxX);
        int hiX = Math.max(minX, maxX);
        int loY = Math.min(minY, maxY);
        int hiY = Math.max(minY, maxY);
        int loZ = Math.min(minZ, maxZ);
        int hiZ = Math.max(minZ, maxZ);
        return hiX >= loX && hiZ >= loZ && hiY >= loY;
    }

    /**
     * 領域内に 3 個のサイコロを出し直す。メインスレッドから呼ぶこと。
     *
     * @return 各サイコロの上面の目（1〜6）、失敗時は空
     */
    public int[] rollThreeDice(Player player) {
        if (!enabled) {
            player.sendMessage("§e[チンチロ] サイコロ表示は無効です。");
            return new int[0];
        }
        World world = Bukkit.getWorld(regionWorld);
        if (world == null || !hasValidRegion()) {
            player.sendMessage("§c[チンチロ] サイコロ領域が未設定です。§7管理者: §f/perocasino chinchiro region set");
            return new int[0];
        }

        int loX = Math.min(minX, maxX);
        int hiX = Math.max(minX, maxX);
        int loY = Math.min(minY, maxY);
        int loZ = Math.min(minZ, maxZ);
        int hiZ = Math.max(minZ, maxZ);

        // edge は配置判定と床からの高さ用。描画だけ小さい環境は model-scale-correction で別途補正する。
        float edge = edgeLengthBlocks;
        double cy = loY + 1.0 + 0.5 * edge;
        double margin = Math.max(separation * 0.5, edge * 0.52);
        double minCx = loX + margin;
        double maxCx = hiX + 1.0 - margin;
        double minCz = loZ + margin;
        double maxCz = hiZ + 1.0 - margin;
        double minCenterDist = Math.max(separation, edge * 1.08);
        // ThreadLocalRandom#nextDouble は origin < bound が必須
        if (!(maxCx > minCx) || !(maxCz > minCz)) {
            player.sendMessage("§c[チンチロ] 領域が狭すぎます。edge-length-blocks を下げるか chinchiro.dice.region を広げてください。");
            return new int[0];
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double[] x = new double[3];
        double[] z = new double[3];
        boolean ok = false;
        for (int attempt = 0; attempt < randomTries; attempt++) {
            for (int i = 0; i < 3; i++) {
                x[i] = rnd.nextDouble(minCx, maxCx);
                z[i] = rnd.nextDouble(minCz, maxCz);
            }
            if (pairwiseOk(x, z, minCenterDist)) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            player.sendMessage("§c[チンチロ] 重ならない配置が見つかりませんでした（random-tries を増やすか領域を広げてください）。");
            return new int[0];
        }

        removeAllDisplays();

        int[] tops = new int[3];
        for (int i = 0; i < 3; i++) {
            tops[i] = rnd.nextInt(1, 7);
            float yaw = rnd.nextFloat((float) (Math.PI * 2.0));
            Location loc = new Location(world, x[i], cy, z[i]);
            try {
                int cx = loc.getBlockX() >> 4;
                int cz = loc.getBlockZ() >> 4;
                if (!world.isChunkLoaded(cx, cz)) {
                    world.loadChunk(cx, cz);
                }
                ItemDisplay display = (ItemDisplay) world.spawnEntity(loc, EntityType.ITEM_DISPLAY);
                display.setItemStack(diceItem());
                // FIXED は ItemDisplay 側の極小スケールが乗りやすいので、立方体は NONE + Transformation のみで制御する
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                display.setBillboard(Display.Billboard.FIXED);
                display.setShadowRadius(0f);
                display.setShadowStrength(0f);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(2);
                display.setInvulnerable(true);
                display.setPersistent(false);
                display.setTransformation(transformationForDice(tops[i], yaw));
                displayUuids.add(display.getUniqueId());
            } catch (Throwable t) {
                plugin.getLogger().warning("[Chinchiro] サイコロ表示のスポーンに失敗: " + t.getMessage());
                removeAllDisplays();
                player.sendMessage("§c[チンチロ] サイコロを表示できませんでした。サーバーログを確認してください。");
                return new int[0];
            }
        }

        return tops;
    }

    private static boolean pairwiseOk(double[] x, double[] z, double minDist) {
        double d2 = minDist * minDist;
        for (int i = 0; i < 3; i++) {
            for (int j = i + 1; j < 3; j++) {
                double dx = x[i] - x[j];
                double dz = z[i] - z[j];
                if (dx * dx + dz * dz < d2) {
                    return false;
                }
            }
        }
        return true;
    }

    private ItemStack diceItem() {
        ItemStack it = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            meta.setDisplayName("§7Chinchiro Dice");
            it.setItemMeta(meta);
        }
        return it;
    }

    /**
     * モデル UV 割当（上面=1, 北=2, 東=3, 西=4, 南=5, 下=6）に合わせ、指定の目がワールド +Y を向く回転にヨーを合成する。
     */
    private Transformation transformationForDice(int top1to6, float yawRad) {
        Quaternionf face = topFaceQuaternion(top1to6);
        Quaternionf yawQ = new Quaternionf().rotateY(yawRad);
        Quaternionf rot = yawQ.mul(face, new Quaternionf()).normalize();
        float s = edgeLengthBlocks * modelScaleCorrection;
        return new Transformation(
                new Vector3f(0f, 0f, 0f),
                rot,
                new Vector3f(s, s, s),
                new Quaternionf()
        );
    }

    private static Quaternionf topFaceQuaternion(int top1to6) {
        return switch (top1to6) {
            case 1 -> new Quaternionf();
            case 6 -> new Quaternionf().rotateX((float) Math.PI);
            case 2 -> new Quaternionf().rotateX((float) (Math.PI / 2.0));
            case 5 -> new Quaternionf().rotateX((float) (-Math.PI / 2.0));
            case 3 -> new Quaternionf().rotateZ((float) (Math.PI / 2.0));
            case 4 -> new Quaternionf().rotateZ((float) (-Math.PI / 2.0));
            default -> new Quaternionf();
        };
    }

    private static float clampEdge(float blocks) {
        if (blocks < 0.05f) return 0.05f;
        if (blocks > 32f) return 32f;
        return blocks;
    }

    private static float clampCorrection(float correction) {
        if (correction < 0.1f) return 0.1f;
        if (correction > 100f) return 100f;
        return correction;
    }
}
