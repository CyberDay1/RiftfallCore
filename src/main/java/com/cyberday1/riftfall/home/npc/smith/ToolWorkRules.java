package com.cyberday1.riftfall.home.npc.smith;

import com.cyberday1.riftfall.home.npc.ResidentRank;

/**
 * The Blacksmith's tool upgrades, pure for the tests. Fortune: one stored level per step to VIII; shards
 * 2/4/6/10/16/64/128/256 plus the step's rarity material (common to epic for I-IV, mythic from V), rare theme crystals
 * for V-VI and faceted crystals for VII-VIII. I-II at Journeyman, III at Expert, IV-VIII at Master; all instant.
 * Pick modes: 4 shards + 2 common material each, from Apprentice.
 * Toolwright: 30% off the shards, rounded down, never below 1. Not tier-capped.
 */
public final class ToolWorkRules {
    public static final int FORTUNE_MAX = 8;
    private static final int MYTHIC = 4;
    private static final int[] FORTUNE_SHARDS = {2, 4, 6, 10, 16, 64, 128, 256};
    private static final int[] FORTUNE_MATERIAL = {2, 2, 2, 2, 1, 2, 3, 4};
    private static final int[] FORTUNE_CRYSTALS = {0, 0, 0, 0, 1, 2, 0, 0};
    private static final int[] FORTUNE_FACETED = {0, 0, 0, 0, 0, 0, 1, 2};
    public static final int MODE_SHARDS = 4, MODE_MATERIAL = 2, MODE_RARITY = 0;
    public static final double TOOLWRIGHT = 0.7;

    private ToolWorkRules() {}

    private static int index(int target) {
        if (target < 1 || target > FORTUNE_MAX) throw new IllegalArgumentException("Fortune level " + target);
        return target - 1;
    }

    public static ResidentRank fortuneRank(int target) {
        index(target);
        return target <= 2 ? ResidentRank.JOURNEYMAN : target == 3 ? ResidentRank.EXPERT : ResidentRank.MASTER;
    }

    public static int discount(int shards, boolean toolwright) {
        return toolwright ? Math.max(1, (int) Math.floor(shards * TOOLWRIGHT)) : shards;
    }

    public static int fortuneShards(int target, boolean toolwright) { return discount(FORTUNE_SHARDS[index(target)], toolwright); }
    /** Index into {@code RarityLadder.RARITIES}: common for I up to mythic for V and above. */
    public static int fortuneRarity(int target) { return Math.min(index(target), MYTHIC); }
    public static int fortuneMaterial(int target) { return FORTUNE_MATERIAL[index(target)]; }
    /** Rare theme crystals ({@code #riftfall:rare_crystals}). */
    public static int fortuneCrystals(int target) { return FORTUNE_CRYSTALS[index(target)]; }
    public static int fortuneFaceted(int target) { return FORTUNE_FACETED[index(target)]; }

    public static int modeShards(boolean toolwright) { return discount(MODE_SHARDS, toolwright); }
}
