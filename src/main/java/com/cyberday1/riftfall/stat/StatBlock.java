package com.cyberday1.riftfall.stat;

import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * One player's point allocation, and nothing about what the points do.
 *
 * <p>
 * What a primary <em>writes into</em> is {@link Derivation}. This carries names and counts only:
 * no amount is computed here.
 *
 * <p>
 * {@code limit} is the budget the allocation was checked against rather than a number this class
 * knows. It is persisted so that a later reduction is detectable instead of silent.
 */
public record StatBlock(Map<Primary, Integer> points, int limit) {

    private static final String LIMIT = "limit";

    /**
     * The seven primaries, as set 2026-08-23.
     *
     * <p>
     * A name on a ledger at this slice. The enum is the NBT key space, which is the only thing a
     * later rename breaks, and {@link RiftStats#VERSION} is what a rename would be handled by.
     */
    public enum Primary {
        STRENGTH,
        DEXTERITY,
        INTELLECT,
        VITALITY,
        SPIRIT,
        AGILITY,
        LUCK;

        /** The NBT key, fixed here so a change to it has to be a deliberate edit. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Primary byKey(String key) {
            for (Primary primary : values()) {
                if (primary.key().equals(key)) {
                    return primary;
                }
            }
            return null;
        }
    }

    public StatBlock {
        int total = 0;
        for (Map.Entry<Primary, Integer> entry : points.entrySet()) {
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException("negative points in " + entry.getKey());
            }
            total += entry.getValue();
        }
        if (total > limit) {
            throw new IllegalArgumentException(total + " points spent against a budget of " + limit);
        }
        // Not `new EnumMap<>(points)`: that copy constructor throws on an empty non-EnumMap, since
        // there is no key to infer the type from.
        Map<Primary, Integer> copy = new EnumMap<>(Primary.class);
        copy.putAll(points);
        points = Collections.unmodifiableMap(copy);
    }

    public static StatBlock empty(int limit) {
        return new StatBlock(Map.of(), limit);
    }

    public int points(Primary primary) {
        return points.getOrDefault(primary, 0);
    }

    public int spent() {
        return points.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int left() {
        return limit - spent();
    }

    /**
     * Whether this many points may go on this primary: the budget is the only ceiling. Since the
     * saturating curve there is no per-primary cap; {@link Derivation} bounds what a point is worth.
     */
    public boolean canSpend(Primary primary, int count) {
        return count >= 0 && count <= left();
    }

    /**
     * Spends points, or throws.
     *
     * <p>
     * Throws rather than returning {@code this} unchanged, because a spend that silently does nothing
     * is the failure a player reports as lost points. Callers that need to ask first have
     * {@link #canSpend}; the screen slice is where that question belongs.
     */
    public StatBlock plus(Primary primary, int count) {
        if (!canSpend(primary, count)) {
            throw new IllegalArgumentException(
                    "cannot spend " + count + " on " + primary + " with " + left() + " left");
        }
        Map<Primary, Integer> next = new EnumMap<>(Primary.class);
        next.putAll(points);
        next.merge(primary, count, Integer::sum);
        return new StatBlock(next, limit);
    }

    /** This block and another, point for point: what a player carries is the class block combined with their own. */
    public StatBlock combined(StatBlock other) {
        Map<Primary, Integer> sum = new EnumMap<>(Primary.class);
        sum.putAll(points);
        other.points.forEach((primary, count) -> sum.merge(primary, count, Integer::sum));
        return new StatBlock(sum, limit + other.limit);
    }

    /** A new budget, raised to what is already spent if it would otherwise invalidate the block. */
    public StatBlock withLimit(int updated) {
        return new StatBlock(points, Math.max(updated, spent()));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        points.forEach((primary, count) -> {
            if (count > 0) {
                tag.putInt(primary.key(), count);
            }
        });
        tag.putInt(LIMIT, limit);
        return tag;
    }

    /**
     * Reads a block, clamping rather than throwing.
     *
     * <p>
     * A throw here would propagate out of {@code SavedData} loading and take the whole file, so every
     * other player's allocation with it. A count that is not positive is dropped, as is a key naming
     * no primary, and a budget below what is already spent is raised to meet it &mdash; that direction
     * because the points are the player's progression and the budget is a number the economy
     * recomputes.
     */
    public static StatBlock load(CompoundTag tag) {
        Map<Primary, Integer> points = new EnumMap<>(Primary.class);
        int total = 0;
        for (String key : tag.getAllKeys()) {
            Primary primary = Primary.byKey(key);
            if (primary == null) {
                continue;
            }
            int count = tag.getInt(key);
            if (count > 0) {
                points.put(primary, count);
                total += count;
            }
        }
        return new StatBlock(points, Math.max(tag.getInt(LIMIT), total));
    }
}
