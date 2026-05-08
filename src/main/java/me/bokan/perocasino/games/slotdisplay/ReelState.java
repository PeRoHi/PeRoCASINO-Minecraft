package me.bokan.perocasino.games.slotdisplay;

import java.util.concurrent.ThreadLocalRandom;

/**
 * リール1本の状態。表示は TextDisplay に出すが、確定情報はここに保持する。
 */
final class ReelState {
    final SlotStrip strip;
    int pos;

    boolean stopRequested;
    Integer targetPos;
    boolean stopped;

    /** 通常回転時の「1コマ進むまでの間隔tick」（大きいほど遅い） */
    int baseStepTicks = 2;

    /** 次の1コマ進めるまでの残りtick */
    int stepCountdownTicks = 0;

    ReelState(SlotStrip strip, int startPos) {
        this.strip = strip;
        this.pos = startPos;
    }

    int distanceToTarget() {
        if (targetPos == null) return Integer.MAX_VALUE;
        int n = strip.size();
        int d = (targetPos - pos) % n;
        if (d < 0) d += n;
        return d;
    }

    int currentSpeedTicks() {
        if (!stopRequested || targetPos == null) {
            return Math.max(1, baseStepTicks);
        }
        int d = distanceToTarget();
        if (d >= 7) return Math.max(1, baseStepTicks);
        if (d >= 4) return Math.max(1, baseStepTicks + 1);
        return Math.max(1, baseStepTicks + 3);
    }

    void tick() {
        if (stopped) return;

        if (stepCountdownTicks > 0) {
            stepCountdownTicks--;
            return;
        }

        pos++;
        int n = strip.size();
        pos = ((pos % n) + n) % n;

        if (stopRequested && targetPos != null && distanceToTarget() == 0) {
            stopped = true;
            return;
        }

        int speed = currentSpeedTicks();
        stepCountdownTicks = Math.max(0, speed - 1);
    }

    /**
     * 「次 or 次の次」で止まるターゲットを確定する。
     * {@code weightNext} と {@code weightNextNext} で、(pos+1) と (pos+2) のどちらで止めるかを抽選する。
     */
    void requestStop(int weightNext, int weightNextNext) {
        if (stopRequested || stopped) return;
        stopRequested = true;
        int n = strip.size();
        if (n <= 1) {
            targetPos = 0;
            return;
        }

        int w1 = Math.max(0, weightNext);
        int w2 = Math.max(0, weightNextNext);
        if (w1 == 0 && w2 == 0) {
            w1 = 1;
            w2 = 1;
        }
        int r = ThreadLocalRandom.current().nextInt(w1 + w2);
        int steps = (r < w1) ? 1 : 2;
        int raw = pos + steps;
        targetPos = ((raw % n) + n) % n;
    }

    void resetForSpin(int startPos) {
        stopRequested = false;
        targetPos = null;
        stopped = false;
        stepCountdownTicks = 0;
        pos = startPos;
    }
}

