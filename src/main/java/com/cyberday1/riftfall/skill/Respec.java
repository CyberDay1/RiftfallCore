package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.progression.RiftXp;
import com.cyberday1.riftfall.stat.StatBlock;
import org.jetbrains.annotations.Nullable;

/**
 * The player's respec, both halves (E-10, ruled 2026-10-11): every skill rank, or every allocated stat
 * point, back for half a level's XP. Pure: the price, whether it can be paid, and what is refunded.
 */
public final class Respec {

    /**
     * Assumed default: the cost is paid only from progress into the current level, never by losing one,
     * since a lost level would also take back skill points. Flip to let the cost eat into the level.
     */
    public static final boolean MAY_DROP_LEVEL = false;

    public enum Result {
        OK, IN_RIFT, NOTHING, NOT_ENOUGH_XP
    }

    /** What a respec would do now: the answer, its price, how much XP is missing, and what comes back. */
    public record Quote(Result result, long cost, long shortBy, int refund) {

        public static final Quote NONE = new Quote(Result.NOTHING, 0L, 0L, 0);

        public boolean ok() {
            return result == Result.OK;
        }
    }

    private Respec() {
    }

    /** Half the XP the player's current level needs for its next step. */
    public static long cost(int level) {
        return RiftXp.required(level) / 2;
    }

    /** The XP a respec may spend at this total under {@code mayDropLevel}. */
    static long spendable(long total, boolean mayDropLevel) {
        if (mayDropLevel) {
            return total;
        }
        int level = RiftXp.levelFor(total, RiftXp.LEVEL_CAP);
        return total - RiftXp.totalFor(level);
    }

    public static Quote quote(long total, int refund, boolean inRift) {
        return quote(total, refund, inRift, MAY_DROP_LEVEL);
    }

    /** The checks in order: the rift lock, then something to refund, then the price. Nothing to refund costs nothing. */
    static Quote quote(long total, int refund, boolean inRift, boolean mayDropLevel) {
        long cost = cost(RiftXp.levelFor(total, RiftXp.LEVEL_CAP));
        if (inRift) {
            return new Quote(Result.IN_RIFT, cost, 0L, refund);
        }
        if (refund <= 0) {
            return new Quote(Result.NOTHING, 0L, 0L, 0);
        }
        long spendable = spendable(total, mayDropLevel);
        if (spendable < cost) {
            return new Quote(Result.NOT_ENOUGH_XP, cost, cost - spendable, refund);
        }
        return new Quote(Result.OK, cost, 0L, refund);
    }

    /** Stat points the player placed themselves: their whole allocation, bonus points included. */
    public static int allocatedStats(StatBlock alloc) {
        return alloc.spent();
    }

    /** The block a stat respec leaves: the class's own, or nothing without a class. */
    public static StatBlock statsReset(@Nullable StatBlock classBlock) {
        return classBlock == null ? StatBlock.empty(0) : classBlock;
    }
}
