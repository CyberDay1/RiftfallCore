package com.cyberday1.riftfall.shield;

import net.minecraft.nbt.CompoundTag;

/**
 * One entity's shield: a single pool every grant adds to, draining in proportion to itself.
 *
 * <p>{@code updatedAt} is the game time the drain was last brought up to. There is no per-shield
 * identity by ruling: no shield has its own timer, so stacked grants merge into one amount.
 */
public record ShieldPool(double amount, long updatedAt) {

    /**
     * Ruled 2026-09-28: "proportial, 5% per 2 seconds". 5% of the CURRENT pool is lost every
     * interval, compounding, so the pool keeps 0.95 of itself per {@link #INTERVAL_TICKS}.
     */
    public static final double KEPT_PER_INTERVAL = 0.95;

    /** The ruled interval: 2 seconds at 20 ticks a second is 40 ticks. */
    public static final long INTERVAL_TICKS = 2L * 20L;

    /**
     * Below this many points the shield is removed outright, because compounding decay never
     * reaches zero on its own. Half of one health point, the smallest unit the hearts show.
     */
    public static final double FLOOR = 0.5;

    public static final ShieldPool EMPTY = new ShieldPool(0.0, 0L);

    static final int VERSION = 1;
    private static final String VERSION_KEY = "version";
    private static final String AMOUNT = "amount";
    private static final String UPDATED = "updated";

    /** Refuses a negative or non-finite amount; an amount under {@link #FLOOR} is no shield at all. */
    public ShieldPool {
        if (!(amount >= 0.0) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException("a shield pool needs a finite non-negative amount, was " + amount);
        }
        if (amount < FLOOR) {
            amount = 0.0;
        }
    }

    public boolean isEmpty() {
        return amount == 0.0;
    }

    /** The pool drained from {@code updatedAt} to {@code now}. Time running backwards is no time. */
    public ShieldPool drainedTo(long now) {
        if (now <= updatedAt) {
            return this;
        }
        double intervals = (double) (now - updatedAt) / INTERVAL_TICKS;
        return new ShieldPool(amount * Math.pow(KEPT_PER_INTERVAL, intervals), now);
    }

    /** A grant. It adds to whatever is standing; it never replaces it. */
    public ShieldPool plus(double granted) {
        return new ShieldPool(amount + granted, updatedAt);
    }

    /** What {@link #absorb} leaves: the pool after, and the part of the hit that got through. */
    public record Absorbed(ShieldPool pool, float absorbed, float remainder) {
    }

    /** Takes {@code damage} out of the pool. Whatever the pool cannot hold is the remainder. */
    public Absorbed absorb(float damage) {
        if (!(damage > 0.0F)) {
            return new Absorbed(this, 0.0F, damage);
        }
        double taken = Math.min(amount, damage);
        return new Absorbed(new ShieldPool(amount - taken, updatedAt), (float) taken, (float) (damage - taken));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_KEY, VERSION);
        tag.putDouble(AMOUNT, amount);
        tag.putLong(UPDATED, updatedAt);
        return tag;
    }

    /** Never throws: a throw here fails the player's whole data load. A bad amount reads as empty. */
    public static ShieldPool load(CompoundTag tag) {
        double amount = tag.getDouble(AMOUNT);
        return new ShieldPool(amount > 0.0 && !Double.isInfinite(amount) ? amount : 0.0, tag.getLong(UPDATED));
    }
}
