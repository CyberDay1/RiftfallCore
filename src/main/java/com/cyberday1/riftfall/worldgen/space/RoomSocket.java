package com.cyberday1.riftfall.worldgen.space;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

/**
 * One face of one chunk section of a room, declared as a place two rooms may join.
 *
 * <p>
 * There is still no width or height here. A socket is a point, a direction and how far up the
 * section face it sits: "this section's face, on this side, has a door, and the door is this high".
 * The physical opening is carved later by the doorway pass, which owns its own dimensions, so the
 * size of the aperture is still not the layout's business. Where it is, is.
 *
 * <p>
 * {@code position} is <em>section-local</em>: {@code 0..size-1} on each axis, relative to the
 * room's own origin, not to the world and not to a block grid.
 */
public record RoomSocket(Vec3i position, Direction direction, int elevation) {

    /**
     * The elevation every authored room in the shipped library uses.
     *
     * <p>
     * Not a default in any meaningful sense &mdash; it is a census. All 1363 ring jigsaws in the 150
     * shipped templates sit here, at {@code y & 15 == 6}, whichever section they are in. Synthetic
     * rooms take it so a test room joins a real one.
     */
    public static final int STANDARD_ELEVATION = 6;

    /** A socket at {@link #STANDARD_ELEVATION}, which is every socket that was not authored otherwise. */
    public RoomSocket(Vec3i position, Direction direction) {
        this(position, direction, STANDARD_ELEVATION);
    }

    /** The section this socket sits in, given the room's placed origin. */
    public Vec3i section(Vec3i roomOrigin) {
        return roomOrigin.offset(position);
    }

    /**
     * The section on the far side of the face &mdash; the one a neighbour would have to occupy,
     * with a socket of its own pointing back, for this socket to be satisfied.
     */
    public Vec3i connectingSection(Vec3i roomOrigin) {
        return roomOrigin.offset(position).relative(direction);
    }

    /**
     * Whether this socket and {@code other} could be the two halves of one doorway: they must face
     * each other, and they must be at the same height up the face they share.
     *
     * <p>
     * The height half is the whole reason {@link #elevation} exists. The seam between two rooms is
     * one block thick and the doorway pass only ever writes into it, so it cannot carry a player
     * from one room's opening up to another's. Two openings that do not line up are two rooms with a
     * wall between them, however well the sections pack.
     */
    public boolean pairsWith(RoomSocket other) {
        return other.direction == direction.getOpposite() && other.elevation == elevation;
    }

    /** Elevation survives untouched: {@link TripleMirror} is the horizontal group and never flips Y. */
    public RoomSocket transformed(TripleMirror mirror, Vec3i untransformedSize) {
        return new RoomSocket(mirror.apply(position, untransformedSize), mirror.applyToDirection(direction),
                elevation);
    }
}
