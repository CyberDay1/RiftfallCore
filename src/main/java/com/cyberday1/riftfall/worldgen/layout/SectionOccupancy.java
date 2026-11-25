package com.cyberday1.riftfall.worldgen.layout;

import com.cyberday1.riftfall.worldgen.space.RiftSpace;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which room owns which chunk section.
 *
 * <p>
 * A plain map from section to room, not a bitmap. The layout is a few hundred rooms over a sparse,
 * unbounded-in-principle volume, so a dense array would have to guess an extent it does not have,
 * and the map buys the reverse lookup &mdash; "who is in this section" &mdash; which the doorway
 * pass will need to ask for every chunk boundary it carves.
 */
public final class SectionOccupancy {

    private final Map<Vec3i, RiftSpace> occupied = new HashMap<>();

    public boolean isFree(Vec3i section) {
        return !occupied.containsKey(section);
    }

    @Nullable
    public RiftSpace occupant(Vec3i section) {
        return occupied.get(section);
    }

    public Set<Vec3i> sections() {
        return Collections.unmodifiableSet(occupied.keySet());
    }

    /**
     * Whether every section of {@code space} is inside {@code shape} and unclaimed.
     *
     * <p>
     * Both conditions are checked over the room's whole volume, not just its socket section. A room
     * that reaches its parent correctly but pokes one corner through the envelope is still a
     * rejection.
     */
    public boolean canPlace(RiftSpace space, RiftShape shape) {
        Vec3i origin = space.origin();
        Vec3i size = space.size();
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    int sx = origin.getX() + x;
                    int sy = origin.getY() + y;
                    int sz = origin.getZ() + z;
                    if (!shape.isSectionValid(sx, sy, sz)) {
                        return false;
                    }
                    if (occupied.containsKey(new Vec3i(sx, sy, sz))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Gives back every section {@code space} claimed, for a placement that turned out not to lead
     * anywhere.
     *
     * <p>
     * The one undo in the layout, and it exists for one caller. A room that is only worth placing if
     * something else will fit past it &mdash; the boss approach, which is a staircase to nowhere
     * unless the arena lands on the far end of it &mdash; cannot be tested for without being
     * claimed first, because the thing being tested must not be allowed to overlap it. Speculate,
     * then take it back.
     */
    public void release(RiftSpace space) {
        space.forEachSection(section -> {
            if (!occupied.remove(section, space)) {
                throw new IllegalStateException("Section " + section + " was not held by " + space);
            }
        });
    }

    /** Claims every section of {@code space}. Throws rather than overwrite: a clash here is a bug upstream. */
    public void place(RiftSpace space) {
        space.forEachSection(section -> {
            RiftSpace previous = occupied.put(section, space);
            if (previous != null) {
                throw new IllegalStateException("Section " + section + " claimed twice: " + previous + " and " + space);
            }
        });
    }
}
