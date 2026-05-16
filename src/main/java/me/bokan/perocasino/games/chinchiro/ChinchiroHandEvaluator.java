package me.bokan.perocasino.games.chinchiro;

import java.util.Arrays;

/**
 * 3個のサイコロの役判定。満貫(456)が最強、次いで嵐、目二つ、タンショ、ヒフミ(123)が最弱。
 */
public final class ChinchiroHandEvaluator {

    private ChinchiroHandEvaluator() {
    }

    public enum Kind {
        MANGAN,
        ARASHI,
        PAIR,
        PLAIN,
        HIFUMI
    }

    /**
     * @param t1 嵐:ゾロ目の値 / 目二つ:対子の目 / タンショ:最大の目
     * @param t2 目二つ:キッカー / タンショ:中間
     * @param t3 タンショ:最小
     */
    public record Score(Kind kind, int t1, int t2, int t3) implements Comparable<Score> {

        private static int kindTier(Kind k) {
            return switch (k) {
                case MANGAN -> 5;
                case ARASHI -> 4;
                case PAIR -> 3;
                case PLAIN -> 2;
                case HIFUMI -> 1;
            };
        }

        @Override
        public int compareTo(Score o) {
            int r = Integer.compare(kindTier(this.kind), kindTier(o.kind));
            if (r != 0) {
                return r;
            }
            return switch (kind) {
                case MANGAN, HIFUMI -> 0;
                case ARASHI -> Integer.compare(t1, o.t1);
                case PAIR -> {
                    int c = Integer.compare(t1, o.t1);
                    if (c != 0) {
                        yield c;
                    }
                    yield Integer.compare(t2, o.t2);
                }
                case PLAIN -> {
                    int c = Integer.compare(t1, o.t1);
                    if (c != 0) {
                        yield c;
                    }
                    c = Integer.compare(t2, o.t2);
                    if (c != 0) {
                        yield c;
                    }
                    yield Integer.compare(t3, o.t3);
                }
            };
        }
    }

    public static Score score(int[] dice) {
        if (dice == null || dice.length != 3) {
            return new Score(Kind.PLAIN, 0, 0, 0);
        }
        int[] d = Arrays.copyOf(dice, 3);
        Arrays.sort(d);
        if (d[0] == 4 && d[1] == 5 && d[2] == 6) {
            return new Score(Kind.MANGAN, 0, 0, 0);
        }
        if (d[0] == 1 && d[1] == 2 && d[2] == 3) {
            return new Score(Kind.HIFUMI, 0, 0, 0);
        }
        if (d[0] == d[2]) {
            return new Score(Kind.ARASHI, d[0], 0, 0);
        }
        if (d[0] == d[1]) {
            return new Score(Kind.PAIR, d[0], d[2], 0);
        }
        if (d[1] == d[2]) {
            return new Score(Kind.PAIR, d[2], d[0], 0);
        }
        return new Score(Kind.PLAIN, d[2], d[1], d[0]);
    }

    /** 正なら a の手が強い（同値は 0）。 */
    public static int compareHands(int[] a, int[] b) {
        return score(a).compareTo(score(b));
    }

    public static String formatDice(int[] dice) {
        if (dice == null || dice.length != 3) {
            return "-";
        }
        return dice[0] + "-" + dice[1] + "-" + dice[2];
    }

    public static String describeJapanese(int[] dice) {
        Score s = score(dice);
        return switch (s.kind()) {
            case MANGAN -> "満貫 (4-5-6)";
            case HIFUMI -> "ヒフミ (1-2-3)";
            case ARASHI -> s.t1() + "の嵐";
            case PAIR -> s.t1() + "の目二つ（キッカー " + s.t2() + "）";
            case PLAIN -> "タンショ " + formatDice(dice);
        };
    }
}
