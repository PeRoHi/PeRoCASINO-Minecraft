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

    public SlotSymbol(String id, String glyph, int weight) {
        this.id = Objects.requireNonNull(id, "id");
        this.glyph = Objects.requireNonNull(glyph, "glyph");
        this.weight = Math.max(0, weight);
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
}

