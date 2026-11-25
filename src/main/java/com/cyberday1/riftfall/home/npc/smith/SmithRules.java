package com.cyberday1.riftfall.home.npc.smith;

import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;

import java.util.Optional;

/**
 * The Blacksmith's numbers, kept pure for the tests. Re-temper (B1): one tier per step, never above the player's tier
 * cap, at most 3 / 6 / unlimited steps per item lifetime by rank; a step to tier t costs t shards and ceil(t / 4)
 * tempering stock, always instant (orders never hold run gear, RULED 2026-11-02). Salvage: the item's rarity material, min(4, band) of it,
 * and floor(T x weight / 3) stock (at least 1), weight 1 (common or none) to 5 (mythic).
 */
public final class SmithRules {
    public enum Kind { WEAPON, ARMOUR, TRINKET }

    public static final int MAX_MATERIAL = 4;
    /** Weaponsmith and Armourer: 40% off their own kind (rounded up, never below 1). */
    public static final double PATH_DISCOUNT = 0.6;
    /** Prospector (a Quarry path): salvage stock +25%, rounded down. */
    public static final int PROSPECTOR_BONUS_DIVISOR = 4;

    private SmithRules() {}

    /** Re-temper steps an item may take over its lifetime at this rank; 0 below Journeyman. */
    public static int band(ResidentRank rank) {
        return switch (rank) {
            case APPRENTICE -> 0;
            case JOURNEYMAN -> 3;
            case EXPERT -> 6;
            case MASTER -> Integer.MAX_VALUE;
        };
    }

    /** Weapons and armour from Journeyman, trinkets from Expert. */
    public static boolean rankAllows(Kind kind, ResidentRank rank) {
        return rank.atLeast(kind == Kind.TRINKET ? ResidentRank.EXPERT : ResidentRank.JOURNEYMAN);
    }

    public enum Step { OK, BAND, CAP }

    /** Whether one more step from {@code tier}, with {@code steps} taken, stays inside the band and the player's cap. */
    public static Step step(int tier, int steps, int playerCap, ResidentRank rank) {
        if (steps >= band(rank)) return Step.BAND;
        if (tier + 1 > playerCap) return Step.CAP;
        return Step.OK;
    }

    static boolean discounted(Kind kind, Optional<String> path) {
        return kind == Kind.WEAPON && path.filter(ResidentPaths.WEAPONSMITH::equals).isPresent()
                || kind == Kind.ARMOUR && path.filter(ResidentPaths.ARMOURER::equals).isPresent();
    }

    private static int discount(int amount, Kind kind, Optional<String> path) {
        return discounted(kind, path) ? Math.max(1, (int) Math.ceil(amount * PATH_DISCOUNT)) : amount;
    }

    /** Shards for one step to tier {@code target}. */
    public static int shards(int target, Kind kind, Optional<String> path) {
        return discount(Math.max(1, target), kind, path);
    }

    /** Tempering stock for one step to tier {@code target}. */
    public static int stock(int target, Kind kind, Optional<String> path) {
        return discount((target + 3) / 4, kind, path);
    }

    /** {@code 1 + (tier - 1) / 5}, the same band the resident XP uses. */
    public static int tierBand(int tier) { return 1 + (Math.max(1, tier) - 1) / 5; }

    /** Rarity material salvage returns; none without a rarity. */
    public static int material(int tier, int rarity) {
        return rarity < 0 ? 0 : Math.min(MAX_MATERIAL, tierBand(tier));
    }

    /** Tempering stock salvage returns. {@code prospector}: the home's Quarry took the Prospector path. */
    public static int salvageStock(int tier, int rarity, boolean prospector) {
        int weight = Math.max(0, rarity) + 1;
        int stock = Math.max(1, Math.max(1, tier) * weight / 3);
        return prospector ? stock + stock / PROSPECTOR_BONUS_DIVISOR : stock;
    }
}
