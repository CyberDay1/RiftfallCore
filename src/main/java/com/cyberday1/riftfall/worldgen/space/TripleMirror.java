package com.cyberday1.riftfall.worldgen.space;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * The eight-element symmetry group of the horizontal square, used in place of vanilla's
 * {@code Rotation}/{@code Mirror} pair.
 *
 * <p>
 * The generators are an x-flip, a z-flip, and a diagonal x&harr;z swap. Every element is a
 * combination of the three, so the group is exactly 8 elements and each element is its own
 * canonical form &mdash; there is no "rotate 180 then mirror equals mirror then rotate 180"
 * normalisation step to get wrong. That matters because one authored room becomes up to eight
 * placeable orientations and the sockets have to be permuted to match, exactly, every time.
 *
 * <p>
 * Y is never touched. Rooms are packed on a horizontal plan and a vertical flip would put ceilings
 * on the floor.
 *
 * <p>
 * Positions are transformed <em>within a box</em>: {@link #apply(Vec3i, Vec3i)} takes the
 * untransformed size and maps {@code [0,size)} onto {@code [0,transformSize(size))}. Composition
 * therefore threads the size through: {@code a.andThen(b)} applied to {@code p} in box {@code s}
 * equals {@code b.apply(a.apply(p, s), a.transformSize(s))}.
 */
public record TripleMirror(boolean flipX, boolean flipZ, boolean swapXZ) {

    public static final TripleMirror IDENTITY = new TripleMirror(false, false, false);

    /**
     * All eight elements, in a stable order. The index is
     * {@code (flipX ? 1 : 0) | (flipZ ? 2 : 0) | (swapXZ ? 4 : 0)}, so a serialized layout can
     * store one byte per room.
     */
    public static final List<TripleMirror> PERMUTATIONS = List.of(
            new TripleMirror(false, false, false),
            new TripleMirror(true, false, false),
            new TripleMirror(false, true, false),
            new TripleMirror(true, true, false),
            new TripleMirror(false, false, true),
            new TripleMirror(true, false, true),
            new TripleMirror(false, true, true),
            new TripleMirror(true, true, true));

    public static TripleMirror byIndex(int index) {
        return PERMUTATIONS.get(index);
    }

    public static TripleMirror random(RandomSource random) {
        return PERMUTATIONS.get(random.nextInt(PERMUTATIONS.size()));
    }

    public int index() {
        return (flipX ? 1 : 0) | (flipZ ? 2 : 0) | (swapXZ ? 4 : 0);
    }

    /** The size of the box after this transform. Only the diagonal swap changes it. */
    public Vec3i transformSize(Vec3i size) {
        return swapXZ ? new Vec3i(size.getZ(), size.getY(), size.getX()) : size;
    }

    /**
     * Maps a position inside a box of {@code size} onto its image inside
     * {@code transformSize(size)}. {@code size} is the <em>untransformed</em> size; passing the
     * already-transformed size is the easy mistake here and silently mirrors about the wrong plane
     * for any non-cubic room.
     */
    public Vec3i apply(Vec3i pos, Vec3i size) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int sx = size.getX();
        int sz = size.getZ();
        if (swapXZ) {
            int t = x;
            x = z;
            z = t;
            int ts = sx;
            sx = sz;
            sz = ts;
        }
        if (flipX) {
            x = sx - 1 - x;
        }
        if (flipZ) {
            z = sz - 1 - z;
        }
        return new Vec3i(x, y, z);
    }

    /** Vertical directions pass through untouched; horizontals follow the same swap-then-flip order. */
    public Direction applyToDirection(Direction direction) {
        if (direction.getAxis().isVertical()) {
            return direction;
        }
        Direction result = swapXZ ? swapAxes(direction) : direction;
        if (flipX && result.getAxis() == Direction.Axis.X) {
            result = result.getOpposite();
        }
        if (flipZ && result.getAxis() == Direction.Axis.Z) {
            result = result.getOpposite();
        }
        return result;
    }

    /**
     * The element equivalent to applying {@code this} and then {@code next}.
     *
     * <p>
     * Derived by searching the eight elements for the one that agrees on all four horizontal
     * directions rather than by hand-writing the multiplication table. The group acts faithfully
     * on those four directions, so the match is unique, and a table typo is impossible.
     */
    public TripleMirror andThen(TripleMirror next) {
        for (TripleMirror candidate : PERMUTATIONS) {
            boolean matches = true;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (candidate.applyToDirection(direction) != next.applyToDirection(applyToDirection(direction))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return candidate;
            }
        }
        throw new IllegalStateException("TripleMirror is not closed under composition: " + this + " then " + next);
    }

    public TripleMirror inverse() {
        for (TripleMirror candidate : PERMUTATIONS) {
            if (andThen(candidate).equals(IDENTITY)) {
                return candidate;
            }
        }
        throw new IllegalStateException("TripleMirror has no inverse for " + this);
    }

    private static Direction swapAxes(Direction direction) {
        return switch (direction) {
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            case WEST -> Direction.NORTH;
            case NORTH -> Direction.WEST;
            default -> direction;
        };
    }
}
