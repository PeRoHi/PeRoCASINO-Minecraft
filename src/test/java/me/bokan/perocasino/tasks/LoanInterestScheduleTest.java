package me.bokan.perocasino.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanInterestScheduleTest {

    @Test
    void nextDueIsNowPlusFiveMinutesNotStalePlusInterval() {
        long now = 1_000_000L;
        long staleDue = now - (3 * LoanInterestSchedule.INTERVAL_MS);
        assertEquals(now + LoanInterestSchedule.INTERVAL_MS, LoanInterestSchedule.nextAfterApply(now));
        // 旧実装 (staleDue + interval) だとまだ過去になり、翌秒に再適用される
        assertTrue(staleDue + LoanInterestSchedule.INTERVAL_MS < now);
    }
}
