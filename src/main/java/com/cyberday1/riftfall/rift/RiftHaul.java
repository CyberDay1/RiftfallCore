package com.cyberday1.riftfall.rift;

/**
 * What one entrant found in one run, counted for the recap: barrels gone through, Rift Shards, gear
 * pieces and the best rarity among them. {@code bestRarity} is an index into
 * {@code RarityLadder.RARITIES}, or {@link #NO_RARITY}.
 */
public record RiftHaul(int barrels, int shards, int gear, int bestRarity) {

    public static final int NO_RARITY = -1;

    public static final RiftHaul EMPTY = new RiftHaul(0, 0, 0, NO_RARITY);

    public RiftHaul {
        barrels = Math.max(0, barrels);
        shards = Math.max(0, shards);
        gear = Math.max(0, gear);
        bestRarity = Math.max(NO_RARITY, bestRarity);
    }

    /** One more barrel looted, and what its share held. */
    public RiftHaul barrel(int shards, int gear, int rarity) {
        return new RiftHaul(barrels + 1, this.shards + shards, this.gear + gear, Math.max(bestRarity, rarity));
    }

    /** Loot found outside a barrel, such as a boss drop. */
    public RiftHaul found(int shards, int gear, int rarity) {
        return new RiftHaul(barrels, this.shards + shards, this.gear + gear, Math.max(bestRarity, rarity));
    }

    public boolean isEmpty() {
        return barrels == 0 && shards == 0 && gear == 0;
    }
}
