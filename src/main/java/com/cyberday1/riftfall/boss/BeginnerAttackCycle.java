package com.cyberday1.riftfall.boss;

import java.util.UUID;

/** A committed attack always spends its recovery, including a miss or an interrupted wind-up. */
final class BeginnerAttackCycle {
    static final int WINDUP_TICKS = 20;
    static final int RECOVERY_TICKS = 50;
    static final int PLANTED_RECOVERY_TICKS = 20;
    enum Action { NONE, WARN, HIT, MISS }
    private long strikeAt = -1, readyAt, plantedUntil;
    private UUID committed;

    boolean windingUp() { return strikeAt >= 0; }
    boolean planted(long now) { return windingUp() || now < plantedUntil; }

    Action update(long now, UUID target, boolean inStartRange, boolean canLand) {
        if (windingUp()) {
            if (now < strikeAt) return Action.NONE;
            boolean hit = committed.equals(target) && canLand;
            strikeAt = -1; committed = null; readyAt = now + RECOVERY_TICKS;
            plantedUntil = now + PLANTED_RECOVERY_TICKS;
            return hit ? Action.HIT : Action.MISS;
        }
        if (target == null || !inStartRange || now < readyAt) return Action.NONE;
        committed = target; strikeAt = now + WINDUP_TICKS;
        return Action.WARN;
    }

    void cancel(long now) {
        if (windingUp()) {
            strikeAt = -1; committed = null; readyAt = now + RECOVERY_TICKS;
            plantedUntil = now + PLANTED_RECOVERY_TICKS;
        }
    }
}
