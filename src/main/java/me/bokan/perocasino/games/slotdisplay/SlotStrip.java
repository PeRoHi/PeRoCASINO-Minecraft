package me.bokan.perocasino.games.slotdisplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * リールの並び（マーク順）。テクスチャの順番は後から設定で差し替え可能にする。
 */
public final class SlotStrip {
    private final List<String> symbolIds;

    public SlotStrip(List<String> symbolIds) {
        Objects.requireNonNull(symbolIds, "symbolIds");
        if (symbolIds.isEmpty()) throw new IllegalArgumentException("symbolIds is empty");
        this.symbolIds = Collections.unmodifiableList(new ArrayList<>(symbolIds));
    }

    public int size() {
        return symbolIds.size();
    }

    public String at(int index) {
        int n = size();
        int i = ((index % n) + n) % n;
        return symbolIds.get(i);
    }

    public List<String> asList() {
        return symbolIds;
    }
}

