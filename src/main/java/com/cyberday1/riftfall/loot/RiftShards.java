package com.cyberday1.riftfall.loot;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Every rift-shard drop number, in one place. Shards are rift-only and never crafted, so this is the whole supply:
 * a once-per-share roll in each barrel and a guaranteed boss drop, both rising with the rift's tier.
 */
public final class RiftShards {

    /** Where a shard roll happens. Barrel grades match {@code BarrelTier}'s suffixes. */
    public enum Grade {
        //    chance%  min  max  tiers per +1
        SHABBY(10, 1, 2, 3),
        WOOD(15, 1, 3, 3),
        IRON(25, 2, 4, 3),
        GOLD(50, 3, 6, 3),
        /** Guaranteed: {@code tier} to {@code tier + 2}, so the boss alone averages the next key's price. */
        BOSS(100, 1, 3, 1);

        private final int chancePercent;
        private final int min;
        private final int max;
        private final int tiersPerShard;

        Grade(int chancePercent, int min, int max, int tiersPerShard) {
            this.chancePercent = chancePercent;
            this.min = min;
            this.max = max;
            this.tiersPerShard = tiersPerShard;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Nullable
        public static Grade of(String key) {
            for (Grade grade : values()) {
                if (grade.key().equals(key)) {
                    return grade;
                }
            }
            return null;
        }

        /** Percent chance a roll yields shards at all. */
        public int chancePercent() {
            return chancePercent;
        }

        /** Shards added at this tier: one per {@code tiersPerShard} tiers above the first. */
        public int bonus(int tier) {
            return (Math.max(1, tier) - 1) / tiersPerShard;
        }

        public int min(int tier) {
            return min + bonus(tier);
        }

        public int max(int tier) {
            return max + bonus(tier);
        }

        /** Expected shards from one roll at this tier. */
        public double expected(int tier) {
            return chancePercent / 100.0 * (min(tier) + max(tier)) / 2.0;
        }
    }

    @Nullable
    private static volatile Integer tierOverride;

    private RiftShards() {
    }

    /** The tier a draw prices at: the rift's, unless a probe has pinned one. */
    public static int tier(int levelTier) {
        Integer override = tierOverride;
        return override != null ? override : levelTier;
    }

    /** Probe use only; no command reaches it. */
    public static void overrideTierForProbe(@Nullable Integer tier) {
        tierOverride = tier;
    }
}
