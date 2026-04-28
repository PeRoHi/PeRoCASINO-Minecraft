package me.bokan.perocasino.roulette;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ルーレット表示（ItemDisplay）管理。
 *
 * - /perocasino roulette display set で壁面3×3相当の表示を設置
 * - ルーレットSPINNING中に回転し、停止角度へ同期して止める
 */
public final class RouletteDisplayService {

    private static final int ROULETTE_CMD = 9001; // paper: custom_model_data

    private final Plugin plugin;

    private UUID displayUuid;
    private Location anchor; // displayの中心位置（壁面から少し前）
    private BlockFace face = BlockFace.NORTH; // 壁の向き（表示面）

    // 回転状態
    private float currentDeg; // 0..360
    private Float targetDeg;  // nullならフリー回転（絶対角）
    private Float targetDegAbsolute; // current基準で「必ず前進して止まる」用（絶対角、単調増加）

    // 簡易回転タスク
    private BukkitRunnable task;

    public RouletteDisplayService(Plugin plugin) {
        this.plugin = plugin;
    }

    /** 画像の真上(0°)を実際の上方向に合わせるための補正角度。 */
    private float angleOffsetDeg = 0f;
    private float decelDistanceDeg = 540f;
    private int decelTicks = 60;

    public void reloadFromConfig() {
        FileConfiguration cfg = plugin.getConfig();
        String worldName = cfg.getString("roulette.display.anchor.world", "");
        String uuidStr = cfg.getString("roulette.display.uuid", "");
        if (worldName == null || worldName.isBlank()) {
            displayUuid = null;
            anchor = null;
            angleOffsetDeg = (float) cfg.getDouble("roulette.display.angle-offset-deg", 0.0);
            decelDistanceDeg = (float) cfg.getDouble("roulette.display.decel-distance-deg", 540.0);
            decelTicks = Math.max(1, cfg.getInt("roulette.display.decel-ticks", 60));
            return;
        }

        World w = Bukkit.getWorld(worldName);
        if (w == null) return;

        displayUuid = tryParseUuid(uuidStr);
        double x = cfg.getDouble("roulette.display.anchor.x", 0.0);
        double y = cfg.getDouble("roulette.display.anchor.y", 0.0);
        double z = cfg.getDouble("roulette.display.anchor.z", 0.0);
        anchor = new Location(w, x, y, z);
        String faceStr = cfg.getString("roulette.display.face", BlockFace.NORTH.name());
        try {
            face = BlockFace.valueOf(faceStr);
        } catch (Exception ignored) {
            face = BlockFace.NORTH;
        }
        angleOffsetDeg = (float) cfg.getDouble("roulette.display.angle-offset-deg", 0.0);
        decelDistanceDeg = (float) cfg.getDouble("roulette.display.decel-distance-deg", 540.0);
        decelTicks = Math.max(1, cfg.getInt("roulette.display.decel-ticks", 60));

        // uuidが無い/不正なら、起動時に生成できる状態にしておく
        if (displayUuid == null) {
            ensureSpawned();
        }
    }

    public boolean hasDisplay() {
        return getDisplay() != null;
    }

    /**
     * ルーレット表示を削除し、設定もクリアする。
     */
    public void removeDisplay() {
        // 回転タスク停止
        if (task != null) {
            task.cancel();
            task = null;
        }
        targetDeg = null;

        // entity削除
        ItemDisplay d = getDisplay();
        if (d != null) {
            d.remove();
        }

        displayUuid = null;
        anchor = null;

        // configクリア
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("roulette.display.anchor.world", "");
        cfg.set("roulette.display.anchor.x", null);
        cfg.set("roulette.display.anchor.y", null);
        cfg.set("roulette.display.anchor.z", null);
        cfg.set("roulette.display.face", null);
        cfg.set("roulette.display.uuid", "");
        plugin.saveConfig();
    }

    public void setAnchor(Location anchorCenter, BlockFace face) {
        // 既存があれば削除（移動/貼り替え）
        ItemDisplay existing = getDisplay();
        if (existing != null) {
            existing.remove();
            displayUuid = null;
        }
        this.anchor = anchorCenter;
        if (face != null) this.face = face;
        ensureSpawned();

        // config保存
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("roulette.display.anchor.world", anchor.getWorld().getName());
        cfg.set("roulette.display.anchor.x", anchor.getX());
        cfg.set("roulette.display.anchor.y", anchor.getY());
        cfg.set("roulette.display.anchor.z", anchor.getZ());
        cfg.set("roulette.display.face", this.face.name());
        cfg.set("roulette.display.uuid", displayUuid == null ? "" : displayUuid.toString());
        plugin.saveConfig();
    }

    public void startSpinning() {
        if (getDisplay() == null) return;
        if (task != null) return;

        targetDeg = null;
        targetDegAbsolute = null;
        // ランダムな初速っぽい開始角
        currentDeg = ThreadLocalRandom.current().nextInt(360);

        task = new BukkitRunnable() {
            // 遅めの回転（必要なら後でconfig化）
            private float speedDegPerTick = 8f;
            private int decelElapsed = 0;
            private Float decelStartDeg = null;

            @Override
            public void run() {
                ItemDisplay d = getDisplay();
                if (d == null) {
                    stopTask();
                    return;
                }

                if (targetDegAbsolute != null) {
                    if (decelStartDeg == null) {
                        decelStartDeg = currentDeg;
                        decelElapsed = 0;
                    }
                    decelElapsed++;
                    float t = Math.min(1.0f, (float) decelElapsed / (float) decelTicks);
                    float eased = easeOutCubic(t);
                    currentDeg = decelStartDeg + (targetDegAbsolute - decelStartDeg) * eased;
                    if (t >= 1.0f) {
                        // 最終tickは必ず狙った角度へスナップし、補間揺れを残さない
                        currentDeg = targetDegAbsolute;
                        applyTransform(d, normalizeDeg(currentDeg));
                        stopTask();
                        return;
                    }
                } else {
                    currentDeg += speedDegPerTick;
                }

                applyTransform(d, normalizeDeg(currentDeg));
            }

            private void stopTask() {
                if (task != null) {
                    task.cancel();
                    task = null;
                }
            }
        };

        task.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 結果の停止角度(0..359)へ止める。
     * 角度0°は「真上(針)」基準。画像側の「上」とズレる場合は angle-offset-deg で補正する。
     */
    public void stopAtAngle(int targetAngleDeg0to359) {
        if (getDisplay() == null) return;
        // 針は上(0°)固定なので、結果角度を上へ持ってくるには盤面を逆方向へ回す。
        // angleOffsetDeg は applyTransform 側で加算されるため、ここでは逆補正を含める。
        float resultAngle = normalizeDeg((targetAngleDeg0to359 % 360 + 360) % 360);
        float aim = normalizeDeg(-resultAngle - angleOffsetDeg);
        targetDeg = aim;

        float cur = currentDeg;
        float desiredDistance = Math.max(360f, decelDistanceDeg);
        float targetAbs = cur + desiredDistance;
        float targetNorm = normalizeDeg(targetAbs);
        float correction = (aim - targetNorm + 360f) % 360f;
        if (correction > 180f) {
            correction -= 360f;
        }
        targetDegAbsolute = targetAbs + correction;

        if (task == null) {
            ItemDisplay d = getDisplay();
            if (d != null) {
                currentDeg = targetDegAbsolute;
                applyTransform(d, normalizeDeg(currentDeg));
            }
        }
    }

    private ItemDisplay getDisplay() {
        if (anchor == null || anchor.getWorld() == null) return null;
        if (displayUuid != null) {
            for (Entity e : anchor.getWorld().getEntities()) {
                if (e.getUniqueId().equals(displayUuid) && e instanceof ItemDisplay d) {
                    return d;
                }
            }
        }
        return null;
    }

    private void ensureSpawned() {
        if (anchor == null || anchor.getWorld() == null) return;
        ItemDisplay existing = getDisplay();
        if (existing != null) {
            applyTransform(existing, currentDeg);
            return;
        }

        // 壁面から少し前に押し出して「貼り付け」を作る
        Vector3f n = faceNormal(face);
        Location spawn = anchor.clone().add(n.x() * 0.51, n.y() * 0.51, n.z() * 0.51);

        ItemDisplay display = (ItemDisplay) anchor.getWorld().spawnEntity(spawn, EntityType.ITEM_DISPLAY);
        display.setItemStack(rouletteItem());
        display.setBillboard(Display.Billboard.FIXED);
        display.setShadowRadius(0f);
        display.setShadowStrength(0f);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
        applyTransform(display, currentDeg);
        displayUuid = display.getUniqueId();
    }

    private ItemStack rouletteItem() {
        ItemStack it = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(ROULETTE_CMD);
            meta.setDisplayName("§d§lRoulette Display");
            it.setItemMeta(meta);
        }
        return it;
    }

    private void applyTransform(ItemDisplay display, float angleDeg) {
        // 壁貼り3×3相当: X/Yを3倍。Zは薄く。
        Vector3f scale = new Vector3f(3.0f, 3.0f, 0.01f);
        Vector3f translation = new Vector3f(0f, 0f, 0f);

        float effectiveAngle = normalizeDeg(angleDeg + angleOffsetDeg);

        // 壁に向ける回転（Y軸）→盤面回転（Z軸）
        float yaw = yawFromFace(face);
        Quaternionf toWall = new Quaternionf().rotateY((float) Math.toRadians(yaw));
        Quaternionf spin = new Quaternionf().rotateZ((float) Math.toRadians(effectiveAngle));
        Quaternionf rot = toWall.mul(spin, new Quaternionf());

        Transformation t = new Transformation(
                translation,
                rot,
                scale,
                new Quaternionf()
        );
        display.setTransformation(t);
    }

    private static float yawFromFace(BlockFace face) {
        // Displayの向き。盤面がプレイヤーに正面を向く想定で調整していく。
        return switch (face) {
            case NORTH -> 180f;
            case SOUTH -> 0f;
            case WEST -> 90f;
            case EAST -> -90f;
            default -> 0f;
        };
    }

    private static Vector3f faceNormal(BlockFace face) {
        return switch (face) {
            case NORTH -> new Vector3f(0f, 0f, -1f);
            case SOUTH -> new Vector3f(0f, 0f, 1f);
            case WEST -> new Vector3f(-1f, 0f, 0f);
            case EAST -> new Vector3f(1f, 0f, 0f);
            case UP -> new Vector3f(0f, 1f, 0f);
            case DOWN -> new Vector3f(0f, -1f, 0f);
            default -> new Vector3f(0f, 0f, 1f);
        };
    }

    private static float normalizeDeg(float deg) {
        float d = deg % 360f;
        if (d < 0) d += 360f;
        return d;
    }

    private static float easeOutCubic(float t) {
        float x = 1.0f - Math.max(0.0f, Math.min(1.0f, t));
        return 1.0f - x * x * x;
    }

    private static float shortestDiffDeg(float from, float to) {
        float a = normalizeDeg(from);
        float b = normalizeDeg(to);
        float diff = b - a;
        if (diff > 180f) diff -= 360f;
        if (diff < -180f) diff += 360f;
        return diff;
    }

    private static UUID tryParseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception ignored) {
            return null;
        }
    }
}

