package me.bokan.perocasino.tasks;

/**
 * 利息の次回時刻。遅延分を一気に連打しない（今から INTERVAL 後）。
 */
public final class LoanInterestSchedule {

    public static final long INTERVAL_MS = 5 * 60 * 1000L;

    private LoanInterestSchedule() {}

    public static long nextAfterApply(long nowMillis) {
        return nowMillis + INTERVAL_MS;
    }
}
