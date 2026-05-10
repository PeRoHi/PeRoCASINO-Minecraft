package me.bokan.perocasino.games.slotdisplay;

import java.util.Objects;

/**
 * スロットのマーク（表示用グリフと抽選重みを保持）。
 *
 * 表示を「読み取る」のではなく、サーバー側で symbolId を確定・保持する。
 */
public final class SlotSymbol {
    private final String id;
    private final String glyph;
    private final int weight;
    /** 役判定（当たりマーク）。デフォルト false */
    private final boolean winning;
    /** ハズレマーク（当たりペアの片側）。{@link #winning} より優先して判定キーに使う */
    private final boolean hazure;
    /**
     * 同一視するマーク種別（例: rapi）。null のときはグループ一致では揃わない。
     * 当たり/外れシンボルは通常ここを付けず {@link #winning}/{@link #hazure} のみ使う。
     */
    private final String matchGroup;

    public SlotSymbol(String id, String glyph, int weight, boolean winning, String matchGroup, boolean hazure) {
        this.id = Objects.requireNonNull(id, "id");
        this.glyph = Objects.requireNonNull(glyph, "glyph");
        this.weight = Math.max(0, weight);
        this.winning = winning;
        this.hazure = hazure;
        this.matchGroup = (matchGroup == null || matchGroup.isBlank()) ? null : matchGroup.trim();
    }

    public SlotSymbol(String id, String glyph, int weight, boolean winning, String matchGroup) {
        this(id, glyph, weight, winning, matchGroup, false);
    }

    public SlotSymbol(String id, String glyph, int weight, boolean winning) {
        this(id, glyph, weight, winning, null, false);
    }

    public SlotSymbol(String id, String glyph, int weight) {
        this(id, glyph, weight, false, null, false);
    }

    public String id() {
        return id;
    }

    public String glyph() {
        return glyph;
    }

    public int weight() {
        return weight;
    }

    public boolean winning() {
        return winning;
    }

    public boolean hazure() {
        return hazure;
    }

    /** グループ役のキー。無い場合は null。 */
    public String matchGroup() {
        return matchGroup;
    }
}
