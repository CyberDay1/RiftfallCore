package com.cyberday1.riftfall.worldgen.layout;

import com.cyberday1.riftfall.rift.RiftKeyItem;

/**
 * Tier-dependent room budgets for timed, lootable dungeons.
 * The main budget includes the entrance and connecting rooms. BranchLayout adds up to one side
 * room per eight budgeted rooms, plus the boss arena and (when needed) its approach.
 * New rifts start near 38 total rooms and saturate near 110, rather than the former 326–974.
 * Depth and radius remain generous headroom; breadth-first growth stops at the room budget.
 * Saved rifts record their sizing version so a later curve cannot reshape already-generated chunks.
 */
public record RiftScale(int radiusSections, BranchLayout.Config layout) {
    /** 2 adds the ore-room quota; a version-1 rift replans exactly as before. */
    public static final int CURRENT_VERSION = 2;
    private static final int DEPTH = 12;
    private static final int RADIUS_SECTIONS = DEPTH * 2;
    private static final int BASE_ROOMS = 32;
    private static final int MAX_ROOMS = 96;
    private static final int HALF_LIFE = 12;

    public static RiftScale forTier(int tier) {
        return forTier(tier, CURRENT_VERSION);
    }

    public static RiftScale forTier(int tier, int version) {
        if (version < 1 || version > CURRENT_VERSION)
            throw new IllegalArgumentException("Unknown rift sizing version " + version);
        int steps = tier <= RiftKeyItem.DEFAULT_TIER ? 0 : tier - RiftKeyItem.DEFAULT_TIER;
        int rooms = BASE_ROOMS + Math.round((MAX_ROOMS - BASE_ROOMS) * (steps / ((float) steps + HALF_LIFE)));
        int ore = version >= 2 ? com.cyberday1.riftfall.worldgen.ore.OreRooms.quota(tier) : 0;
        return new RiftScale(RADIUS_SECTIONS, BranchLayout.Config.sized(DEPTH, rooms, ore));
    }
}
