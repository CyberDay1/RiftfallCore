package com.cyberday1.riftfall.worldgen.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The one place {@link TripleMirror} is translated into the {@code Rotation}/{@code Mirror} pair
 * vanilla's structure placement actually takes.
 *
 * <p>
 * The two describe the same eight symmetries in incompatible ways, and both differences bite.
 * Vanilla's twelve {@code (rotation, mirror)} pairs cover the group four times over &mdash;
 * {@code CLOCKWISE_180} with {@code LEFT_RIGHT} is the same map as {@code NONE} with
 * {@code FRONT_BACK} &mdash; so "the" pair for an element has to be chosen, not computed. And
 * vanilla transforms about a pivot without renormalising, so a rotated template lands partly in
 * negative coordinates, where {@link TripleMirror#apply(Vec3i, Vec3i)} always maps a box onto
 * {@code [0, size)}. A room placed with the raw pair is rotated correctly and in the wrong place.
 *
 * <p>
 * Both are handled by deferring to vanilla rather than restating it: the pair is found by searching
 * for the one whose direction map agrees on all four horizontals, the same faithful-action argument
 * {@link TripleMirror#andThen(TripleMirror)} rests on, and the offset comes from the same two-corner
 * arithmetic {@code StructureTemplate.getBoundingBox} uses. Neither can drift from what
 * {@code placeInWorld} does, because neither is an independent derivation of it.
 */
public final class TemplatePlacement {

    private static final Rotation[] ROTATIONS = Rotation.values();
    private static final Mirror[] MIRRORS = Mirror.values();

    /** Indexed by {@link TripleMirror#index()}. */
    private static final Rotation[] ROTATION_OF = new Rotation[8];
    private static final Mirror[] MIRROR_OF = new Mirror[8];

    static {
        for (TripleMirror element : TripleMirror.PERMUTATIONS) {
            boolean found = false;
            for (Mirror mirror : MIRRORS) {
                for (Rotation rotation : ROTATIONS) {
                    if (!agreesOnDirections(element, rotation, mirror)) {
                        continue;
                    }
                    ROTATION_OF[element.index()] = rotation;
                    MIRROR_OF[element.index()] = mirror;
                    found = true;
                    break;
                }
                if (found) {
                    break;
                }
            }
            if (!found) {
                throw new IllegalStateException("No vanilla rotation/mirror pair matches " + element);
            }
        }
    }

    private TemplatePlacement() {
    }

    public static Rotation rotationOf(TripleMirror mirror) {
        return ROTATION_OF[mirror.index()];
    }

    public static Mirror mirrorOf(TripleMirror mirror) {
        return MIRROR_OF[mirror.index()];
    }

    /**
     * Placement settings that orient a template the way {@code mirror} says. The caller still owns
     * the bounding box, processors and liquid handling.
     */
    public static StructurePlaceSettings settings(TripleMirror mirror) {
        return new StructurePlaceSettings()
                .setRotation(rotationOf(mirror))
                .setMirror(mirrorOf(mirror));
    }

    /**
     * The position to hand {@code placeInWorld} so the oriented template's minimum corner lands
     * exactly on {@code corner}.
     *
     * <p>
     * This is the correction for vanilla not renormalising: under a rotation about the zero pivot
     * the template's blocks run off into negative x or z, and the shift that brings them back is
     * whatever the transformed box's own minimum happens to be.
     *
     * @param size the template's authored, untransformed block extent
     */
    public static BlockPos anchor(Vec3i size, TripleMirror mirror, Vec3i corner) {
        BoundingBox box = transformedBox(size, mirror);
        return new BlockPos(corner.getX() - box.minX(), corner.getY() - box.minY(), corner.getZ() - box.minZ());
    }

    /**
     * Where the oriented template sits in the world once anchored at {@code corner}: a box whose
     * minimum corner is {@code corner} and whose extent is {@code mirror.transformSize(size)}.
     */
    public static BoundingBox boundsAt(Vec3i size, TripleMirror mirror, Vec3i corner) {
        Vec3i extent = mirror.transformSize(size);
        return new BoundingBox(corner.getX(), corner.getY(), corner.getZ(),
                corner.getX() + extent.getX() - 1,
                corner.getY() + extent.getY() - 1,
                corner.getZ() + extent.getZ() - 1);
    }

    /**
     * The box the template occupies about a zero pivot, by the same two-corner argument
     * {@code StructureTemplate.getBoundingBox} makes: the transform is affine on the box, so its
     * image is pinned by the images of two opposite corners.
     */
    private static BoundingBox transformedBox(Vec3i size, TripleMirror mirror) {
        Rotation rotation = rotationOf(mirror);
        Mirror vanillaMirror = mirrorOf(mirror);
        BlockPos low = StructureTemplate.transform(BlockPos.ZERO, vanillaMirror, rotation, BlockPos.ZERO);
        BlockPos high = StructureTemplate.transform(
                new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1),
                vanillaMirror, rotation, BlockPos.ZERO);
        return BoundingBox.fromCorners(low, high);
    }

    private static boolean agreesOnDirections(TripleMirror element, Rotation rotation, Mirror mirror) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (rotation.rotate(mirror.mirror(direction)) != element.applyToDirection(direction)) {
                return false;
            }
        }
        return true;
    }
}
