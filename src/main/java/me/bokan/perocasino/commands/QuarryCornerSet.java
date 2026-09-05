package me.bokan.perocasino.commands;

import java.util.Objects;
import java.util.Optional;

/**
 * 採石場の2点指定。ライブの min を消さず、正規化した立方体を一度に確定する。
 */
public final class QuarryCornerSet {

    private QuarryCornerSet() {}

    public record Corner(String world, int x, int y, int z) {
        public Corner {
            Objects.requireNonNull(world, "world");
        }
    }

    public record Range(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Range {
            Objects.requireNonNull(world, "world");
        }
    }

    public static Optional<Range> complete(Corner first, Corner second) {
        if (first == null || second == null) {
            return Optional.empty();
        }
        if (!first.world().equals(second.world())) {
            return Optional.empty();
        }
        return Optional.of(new Range(
                second.world(),
                Math.min(first.x(), second.x()),
                Math.min(first.y(), second.y()),
                Math.min(first.z(), second.z()),
                Math.max(first.x(), second.x()),
                Math.max(first.y(), second.y()),
                Math.max(first.z(), second.z())
        ));
    }
}
