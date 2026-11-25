package com.cyberday1.riftfall.worldgen.layout;

import net.minecraft.core.Vec3i;

/**
 * The envelope a layout is allowed to fill, tested one chunk section at a time.
 *
 * <p>
 * Kept deliberately dumb: no bounding-box query, no volume, no iteration. Layout only ever asks
 * "may I put a section here", tens of thousands of times, so a single hot predicate is the whole
 * useful surface and anything richer would just be an API to keep consistent with it.
 */
@FunctionalInterface
public interface RiftShape {

    boolean isSectionValid(int x, int y, int z);

    default boolean isSectionValid(Vec3i section) {
        return isSectionValid(section.getX(), section.getY(), section.getZ());
    }

    /** No envelope at all. Useful for measuring what an algorithm does before it hits a wall. */
    static RiftShape unbounded() {
        return (x, y, z) -> true;
    }

    /** An axis-aligned section box, both corners inclusive. */
    static RiftShape box(Vec3i min, Vec3i maxInclusive) {
        int x0 = min.getX();
        int y0 = min.getY();
        int z0 = min.getZ();
        int x1 = maxInclusive.getX();
        int y1 = maxInclusive.getY();
        int z1 = maxInclusive.getZ();
        return (x, y, z) -> x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
    }

    /**
     * A vertical cylinder about the section column {@code x=z=0}: circular in plan, flat-capped in
     * Y. Radius and Y bounds are in sections. Distance is measured squared, so a room at the rim
     * either fits or does not with no floating-point tie-breaking.
     */
    static RiftShape cylinder(int radiusSections, int minY, int maxYInclusive) {
        int rr = radiusSections * radiusSections;
        return (x, y, z) -> y >= minY && y <= maxYInclusive && x * x + z * z <= rr;
    }

    default RiftShape and(RiftShape other) {
        return (x, y, z) -> isSectionValid(x, y, z) && other.isSectionValid(x, y, z);
    }
}
