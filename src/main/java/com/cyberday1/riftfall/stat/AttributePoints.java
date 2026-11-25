package com.cyberday1.riftfall.stat;

import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Attribute points (ruled 2026-11-08): three a level from level 1, plus ten once for containing the
 * Unseated, placed on the primaries on top of the class block. Pure: the pool, and whether a batch may go in.
 *
 * <p>Earned is derived from the live level, never stored, on {@code SkillBook.earned}'s pattern; only the
 * allocation and the Unseated flag live in {@link RiftStats}. {@code available} may read negative if the
 * rate is ever lowered: spending is refused until it recovers and nothing is deleted.
 */
public final class AttributePoints {

    public static final int PER_LEVEL = 3;
    public static final int UNSEATED_BONUS = 10;

    /** What an allocation answers, checked in this order. */
    public enum Allocate {
        OK, IN_RIFT, NO_CLASS, BAD_REQUEST, NOT_ENOUGH_POINTS
    }

    private AttributePoints() {
    }

    public static int earned(int level, boolean unseatedBonus) {
        return PER_LEVEL * Math.max(0, level) + (unseatedBonus ? UNSEATED_BONUS : 0);
    }

    public static int available(int level, boolean unseatedBonus, StatBlock alloc) {
        return earned(level, unseatedBonus) - alloc.spent();
    }

    /** The checks, in the plan's order: rift, class, a well-formed batch, then the pool. */
    public static Allocate check(int level, boolean unseatedBonus, StatBlock alloc, @Nullable String skillClass,
                                 boolean inRift, Map<StatBlock.Primary, Integer> deltas) {
        if (inRift) {
            return Allocate.IN_RIFT;
        }
        if (skillClass == null) {
            return Allocate.NO_CLASS;
        }
        long sum = 0;
        for (int count : deltas.values()) {
            if (count < 0) {
                return Allocate.BAD_REQUEST;
            }
            sum += count;
        }
        if (sum == 0) {
            return Allocate.BAD_REQUEST;
        }
        return sum > available(level, unseatedBonus, alloc) ? Allocate.NOT_ENOUGH_POINTS : Allocate.OK;
    }

    /** The allocation with a checked batch added, its budget the points earned. Throws on a batch {@link #check} refuses. */
    public static StatBlock allocated(int level, boolean unseatedBonus, StatBlock alloc,
                                      Map<StatBlock.Primary, Integer> deltas) {
        Map<StatBlock.Primary, Integer> next = new EnumMap<>(StatBlock.Primary.class);
        next.putAll(alloc.points());
        deltas.forEach((primary, count) -> next.merge(primary, count, Integer::sum));
        return new StatBlock(next, earned(level, unseatedBonus));
    }
}
