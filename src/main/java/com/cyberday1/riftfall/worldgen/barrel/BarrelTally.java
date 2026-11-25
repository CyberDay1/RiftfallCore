package com.cyberday1.riftfall.worldgen.barrel;

import com.cyberday1.riftfall.block.barrel.BarrelTier;

import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How many chest markers one rift offered and how many barrels came of them.
 *
 * <p>
 * The pair is the instrument, for the reason the spawner counters are: a spawn chance is a design
 * number, and the two counts together are what the rooms actually had to give. Atomic because
 * worldgen builds several columns at once.
 */
public final class BarrelTally {

    private final AtomicInteger considered = new AtomicInteger();
    private final AtomicInteger[] placed = new AtomicInteger[BarrelTier.ALL.length];

    public BarrelTally() {
        for (int index = 0; index < placed.length; index++) {
            placed[index] = new AtomicInteger();
        }
    }

    public void consider() {
        considered.incrementAndGet();
    }

    public void place(BarrelTier tier) {
        placed[tier.ordinal()].incrementAndGet();
    }

    public int considered() {
        return considered.get();
    }

    public int placed() {
        int total = 0;
        for (AtomicInteger count : placed) {
            total += count.get();
        }
        return total;
    }

    /** {@code 7 barrels of 340 markers (4 shabby, 2 wood, 1 gold)} — the breakdown skips empty tiers. */
    public String report() {
        StringJoiner tiers = new StringJoiner(", ", " (", ")").setEmptyValue("");
        for (BarrelTier tier : BarrelTier.ALL) {
            int count = placed[tier.ordinal()].get();
            if (count > 0) {
                tiers.add(count + " " + tier.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return placed() + " barrels of " + considered.get() + " markers" + tiers;
    }
}
