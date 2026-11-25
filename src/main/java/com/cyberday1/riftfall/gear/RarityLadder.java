package com.cyberday1.riftfall.gear;

/**
 * How much a rift's tier is allowed to move the rarity of what drops in it.
 *
 * <h2>The ladder is eight rungs and stops there</h2>
 * A rift's tier has no agreed ceiling &mdash; the key forge will sell any number a player can pay for
 * &mdash; so a table has to end somewhere and everything above the end has to mean the same thing.
 * Eight is where {@code RiftPortalBlock.intensityFor} already saturates, so the portal a player walks
 * through stops changing at exactly the tier the loot stops changing, and the two signals agree
 * instead of one of them quietly outrunning the other.
 *
 * <h2>Modest on purpose</h2>
 * Tier moves the odds and never the pool. Mythic runs 1% to 8% and common 52% down to 17%: a tier-8
 * rift is a better place to farm than a tier-1 one, and it is not a different game. Compare what this
 * replaces, which was Apotheosis' own world-tier table at 0% mythic and 60% common on one end and
 * 25% mythic and 0% common on the other &mdash; a ramp steep enough that the bottom of it cannot drop
 * the top two rarities at all.
 *
 * <p>
 * Every row is weights per thousand and every row sums to exactly {@link #TOTAL}. The rounding from
 * interpolating the eight rows is absorbed into {@code common}, which is the only column wide enough
 * for it to be invisible.
 *
 * <h2>What this deliberately does not do</h2>
 * It does not touch <em>which</em> affixes a rarity may draw. That pool is flat and the same size at
 * every rarity by design, and the rarity ladder decides how many are rolled, never which. Nor does it
 * read Apotheosis' world tier: world tiers are not a mechanic Riftfall exposes, and a player's
 * position on that hidden ladder must not be the reason their loot is better.
 */
public final class RarityLadder {

    /** Weights are per thousand. A row that does not sum to this is a bug, and the test says so. */
    public static final int TOTAL = 1000;

    /** The rung above which every rift is the same rift, as far as rarity is concerned. */
    public static final int TOP_TIER = 8;

    /**
     * The five rarities in ascending order, as the ids their datapack files are named by.
     *
     * <p>
     * Strings rather than {@code ResourceLocation} so that the whole of this class stays free of
     * Minecraft and can be tested without one. {@link RiftRarity} is where they become real.
     */
    public static final String[] RARITIES = {
            "apotheosis:common",
            "apotheosis:uncommon",
            "apotheosis:rare",
            "apotheosis:epic",
            "apotheosis:mythic"
    };

    /** One row per tier 1..{@link #TOP_TIER}, five columns in {@link #RARITIES} order. */
    private static final int[][] WEIGHTS = {
            {520, 300, 130, 40, 10},
            {471, 294, 154, 61, 20},
            {419, 289, 179, 83, 30},
            {370, 283, 203, 104, 40},
            {320, 277, 227, 126, 50},
            {271, 271, 251, 147, 60},
            {219, 266, 276, 169, 70},
            {170, 260, 300, 190, 80}
    };

    private RarityLadder() {
    }

    /**
     * The weights a rift of this tier rolls on. Tiers below one and above {@link #TOP_TIER} are held
     * to the ends rather than refused: a tier is a number a player chose, and there is no value of it
     * that should mean "no loot".
     */
    public static int[] weights(int riftTier) {
        return WEIGHTS[Math.clamp(riftTier, 1, TOP_TIER) - 1].clone();
    }

    /**
     * Which rarity a roll lands on, as an index into {@link #RARITIES}.
     *
     * <p>
     * Takes the roll rather than a random source, so the mapping is a total function over
     * {@code [0, TOTAL)} that a test can walk end to end instead of sampling. A roll outside that
     * range would otherwise fall off the bottom of the loop and return mythic, which is the one
     * failure worth being certain cannot happen, so it is clamped.
     */
    public static int index(int riftTier, int roll) {
        int[] weights = WEIGHTS[Math.clamp(riftTier, 1, TOP_TIER) - 1];
        int remaining = Math.clamp(roll, 0, TOTAL - 1);
        for (int rarity = 0; rarity < weights.length - 1; rarity++) {
            remaining -= weights[rarity];
            if (remaining < 0) {
                return rarity;
            }
        }
        return weights.length - 1;
    }

    /** The id of the rarity a roll lands on. */
    public static String rarity(int riftTier, int roll) {
        return RARITIES[index(riftTier, roll)];
    }

    /** Each ascending rarity step receives the stated relative odds bonus. Zero preserves the old ladder exactly. */
    public static int index(int riftTier, int roll, double bonusPercent) {
        if (!Double.isFinite(bonusPercent) || bonusPercent <= 0) return index(riftTier, roll);
        int[] base = weights(riftTier);
        double[] adjusted = new double[base.length];
        double total = 0;
        for (int i = 0; i < base.length; i++) {
            adjusted[i] = base[i] * Math.pow(1 + bonusPercent / 100, i);
            total += adjusted[i];
        }
        double remaining = Math.clamp(roll, 0, TOTAL - 1) / (double) TOTAL * total;
        for (int i = 0; i < adjusted.length - 1; i++) {
            remaining -= adjusted[i];
            if (remaining < 0) return i;
        }
        return adjusted.length - 1;
    }
}
