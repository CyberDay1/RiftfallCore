package com.cyberday1.riftfall.progression;

/**
 * The level ladder: one tier per ten levels, {@code floor(level / 10) + 1}, with no ceiling.
 *
 * <p>
 * No stored boundaries, so there are none to disagree. Tier 1 is levels 1&ndash;9 because levels
 * start at 1; every other tier is ten wide. ⛔ Not the UE5 six-name {@code ELevelTier} this used to
 * transcribe &mdash; the design replaced that ladder, and the names went with it.
 *
 * <p>
 * The key forge gates on this, and the key's own {@code rift_tier} is the same number frozen.
 */
public final class RiftTier {

    /** How many levels one tier spans. */
    public static final int WIDTH = 10;

    private RiftTier() {
    }

    /** The tier a level stands on. */
    public static int of(int level) {
        if (level < RiftXp.FIRST_LEVEL) {
            throw new IllegalArgumentException("level " + level + " is below " + RiftXp.FIRST_LEVEL);
        }
        return level / WIDTH + 1;
    }

    /**
     * The lowest level on a tier, which is the level a player needs to reach it.
     *
     * <p>
     * Saturates at {@code Integer.MAX_VALUE} rather than wrapping, because the forge's tier arrives
     * as a client-controlled {@code VarInt} and a wrapped answer would claim level 1 unlocks it.
     */
    public static int first(int tier) {
        if (tier < 1) {
            throw new IllegalArgumentException("tier " + tier + " is below 1");
        }
        return (int) Math.max(RiftXp.FIRST_LEVEL, Math.min(Integer.MAX_VALUE, (tier - 1L) * WIDTH));
    }
}
