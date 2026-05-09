package me.bokan.perocasino.roulette;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * ルーレットの「針（上）」基準の角度セグメント設定。
 * 画像側もこの設定に合わせて作成する想定。
 */
public final class RouletteAngleConfig {

    /**
     * startDeg: 0〜359（0含む）
     * endDegExclusive: 1〜360（360含む）。end は含まない（[start, end)）
     */
    public record Segment(int multiplier, int startDeg, int endDegExclusive) {
        public int widthDeg() {
            return endDegExclusive - startDeg;
        }
    }

    private final List<Segment> segments;
    private final int totalDegrees;

    public RouletteAngleConfig(List<Segment> segments) {
        this.segments = List.copyOf(segments);
        this.totalDegrees = segments.stream().mapToInt(Segment::widthDeg).sum();
    }

    public List<Segment> segments() {
        return segments;
    }

    public int totalDegrees() {
        return totalDegrees;
    }

    /**
     * `roulette.angle-segments` を読み取って検証する。
     * 期待: 2,4,6,10,20 を含む / 幅は 1°以上 / 合計360° / 重複なし。
     */
    public static RouletteAngleConfig loadAndValidate(FileConfiguration cfg) {
        List<?> raw = cfg.getList("roulette.segments");
        if (raw == null || raw.isEmpty()) {
            throw new IllegalStateException("config.yml に roulette.segments がありません（または空です）。");
        }

        List<SegDef> defs = new ArrayList<>();
        for (Object o : raw) {
            if (!(o instanceof java.util.Map<?, ?> map)) {
                throw new IllegalStateException("roulette.segments の各要素は multiplier/degrees を持つマップである必要があります。");
            }
            Object mObj = map.get("multiplier");
            Object dObj = map.get("degrees");
            if (mObj == null || dObj == null) {
                throw new IllegalStateException("roulette.segments の各要素に multiplier と degrees が必要です。");
            }
            int mult = toInt(mObj, "multiplier");
            int deg = toInt(dObj, "degrees");
            defs.add(new SegDef(mult, deg));
        }

        if (defs.isEmpty()) {
            throw new IllegalStateException("roulette.segments が空です。");
        }

        // 必須倍率がどこかに含まれていること（周回している想定）
        Set<Integer> present = defs.stream().map(SegDef::multiplier).collect(java.util.stream.Collectors.toSet());
        for (int m : List.of(2, 4, 6, 10, 20)) {
            if (!present.contains(m)) {
                throw new IllegalStateException("roulette.segments に倍率 " + m + " が含まれていません。");
            }
        }

        // 幅検証
        for (SegDef d : defs) {
            if (d.degrees < 1) {
                throw new IllegalStateException("roulette.segments の degrees は 1°以上にしてください: " + d.multiplier);
            }
        }

        int sum = defs.stream().mapToInt(SegDef::degrees).sum();
        if (sum != 360) {
            throw new IllegalStateException("roulette.segments の角度合計が 360 ではありません: " + sum);
        }

        // セグメント作成: 0°から順に割り当て（設定順をそのまま採用）
        List<Segment> segments = new ArrayList<>();
        int cursor = 0;
        for (SegDef d : defs) {
            segments.add(new Segment(d.multiplier, cursor, cursor + d.degrees));
            cursor += d.degrees;
        }
        return new RouletteAngleConfig(segments);
    }

    public Segment segmentForAngle(int deg0to359) {
        int a = ((deg0to359 % 360) + 360) % 360;
        for (Segment s : segments) {
            if (a >= s.startDeg && a < s.endDegExclusive) return s;
        }
        // 360合計なら来ない
        return segments.get(segments.size() - 1);
    }

    public boolean isNonDecreasingByMultiplierForCanonicalSet() {
        // 2,4,6,10,20 の "最初の出現" を取り、その degrees が非減少かどうかを見る
        // 画像作成のガイド用途。強制はしない。
        List<Segment> picked = new ArrayList<>();
        for (int m : List.of(2, 4, 6, 10, 20)) {
            Segment s = segments.stream().filter(seg -> seg.multiplier == m).findFirst().orElse(null);
            if (s != null) picked.add(s);
        }
        picked.sort(Comparator.comparingInt(seg -> seg.multiplier));
        for (int i = 1; i < picked.size(); i++) {
            if (picked.get(i).widthDeg() < picked.get(i - 1).widthDeg()) return false;
        }
        return true;
    }

    private record SegDef(int multiplier, int degrees) {}

    private static int toInt(Object v, String label) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("roulette.segments の " + label + " が数値ではありません: " + s);
            }
        }
        throw new IllegalStateException("roulette.segments の " + label + " が数値ではありません: " + v);
    }
}

