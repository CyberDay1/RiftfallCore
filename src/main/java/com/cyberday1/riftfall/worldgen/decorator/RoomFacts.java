package com.cyberday1.riftfall.worldgen.decorator;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/**
 * What the generator knows about one placement that its blocks cannot say: the rift's tier, the boxes
 * its props will be stamped into after it, and whether the rift picked it as an ore room.
 *
 * @param tier     the tier the rift's key was forged at
 * @param reserved world-space prop boxes, which a decorator that adds volume must stay out of
 * @param oreRoom  one of this rift's ore rooms ({@code OreRooms#select})
 */
public record RoomFacts(int tier, List<BoundingBox> reserved, boolean oreRoom) {

    /** Gallery previews, tests and datapack placements: tier 1, nothing reserved, no ore. */
    public static final RoomFacts NONE = new RoomFacts(1, List.of(), false);

    public RoomFacts {
        tier = Math.max(1, tier);
        reserved = List.copyOf(reserved);
    }

    public static RoomFacts tier(int tier) {
        return new RoomFacts(tier, List.of(), false);
    }

    /** Whether {@code box} touches any reserved box. */
    public boolean blocked(BoundingBox box) {
        for (BoundingBox r : reserved) {
            if (r.intersects(box)) return true;
        }
        return false;
    }
}
