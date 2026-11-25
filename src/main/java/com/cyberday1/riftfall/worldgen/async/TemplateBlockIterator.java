package com.cyberday1.riftfall.worldgen.async;

import com.cyberday1.riftfall.worldgen.space.TemplatePlacement;
import com.cyberday1.riftfall.worldgen.space.TripleMirror;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;

/**
 * Iterates a {@link StructureTemplate}'s blocks with rotation/mirror applied, yielding
 * transformed block data without placing anything into a world.
 *
 * <p>
 * Accesses the private {@code palettes} field via reflection since no public getter exists
 * in NeoForge 1.21.1. The transform arithmetic matches {@link TemplatePlacement#anchor} and
 * {@link TemplatePlacement#boundsAt} exactly: same two-corner box, same anchor shift.
 */
public final class TemplateBlockIterator {

    /** Reflected access to {@code StructureTemplate.palettes} (private, no getter). */
    private static final Field PALETTES_FIELD;

    static {
        try {
            PALETTES_FIELD = StructureTemplate.class.getDeclaredField("palettes");
            PALETTES_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("StructureTemplate.palettes not found", e);
        }
    }

    private TemplateBlockIterator() {}

    /** Raw, read-only view for runtime edits of a single-palette authored template. */
    @SuppressWarnings("unchecked")
    public static List<StructureTemplate.StructureBlockInfo> singlePaletteBlocks(StructureTemplate template) {
        try {
            var palettes = (List<StructureTemplate.Palette>) PALETTES_FIELD.get(template);
            if (palettes.size() != 1) throw new IllegalArgumentException("Expected one template palette");
            return java.util.Collections.unmodifiableList(palettes.getFirst().blocks());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read StructureTemplate.palettes", e);
        }
    }

    /** One block from the template, fully transformed into world space. */
    public record Entry(
            BlockPos localPos,
            BlockPos worldPos,
            BlockState state,
            @Nullable CompoundTag nbt,
            int sectionY,
            int flatIndex
    ) {}

    /**
     * Walks every block in the template, applies the mirror/rotation, clips against the bounding
     * box, and invokes the consumer for each surviving block.
     *
     * @param template   the structure template to iterate
     * @param mirror     the rift orientation
     * @param anchor     world-space placement anchor (same as {@link TemplatePlacement#anchor})
     * @param clip       column bounding box to clip against
     * @param knownShape true to also clip against the template's own transformed extent
     * @param consumer   receives each transformed block entry
     */
    @SuppressWarnings("unchecked")
    public static void forEach(StructureTemplate template, TripleMirror mirror, BlockPos anchor,
                               BoundingBox clip, boolean knownShape, Consumer<Entry> consumer) {
        forEach(template, mirror, anchor, clip, knownShape, true, consumer);
    }

    /** Processors see unrotated states; vanilla rotates their final result after decoration. */
    @SuppressWarnings("unchecked")
    public static void forEach(StructureTemplate template, TripleMirror mirror, BlockPos anchor,
                               BoundingBox clip, boolean knownShape, boolean transformStates, Consumer<Entry> consumer) {
        Vec3i size = template.getSize();
        Rotation rotation = TemplatePlacement.rotationOf(mirror);
        Mirror vanillaMirror = TemplatePlacement.mirrorOf(mirror);

        // Known-shape clipping box: the template's own extent about zero pivot after transform.
        BoundingBox templateBox = null;
        if (knownShape) {
            BlockPos low = StructureTemplate.transform(BlockPos.ZERO, vanillaMirror, rotation, BlockPos.ZERO);
            BlockPos high = StructureTemplate.transform(
                    new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1),
                    vanillaMirror, rotation, BlockPos.ZERO);
            templateBox = BoundingBox.fromCorners(low, high);
        }

        List<StructureTemplate.Palette> palettes;
        try {
            palettes = (List<StructureTemplate.Palette>) PALETTES_FIELD.get(template);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read StructureTemplate.palettes", e);
        }

        if (!palettes.isEmpty()) {
            StructureTemplate.Palette palette = TemplatePlacement.settings(mirror).getRandomPalette(palettes, anchor);
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Room bake interrupted");
                BlockPos localPos = info.pos();

                // Apply vanilla rotation+mirror about zero pivot, same as placeInWorld internally.
                BlockPos rotated = StructureTemplate.transform(localPos, vanillaMirror, rotation, BlockPos.ZERO);

                // Shift by anchor so the minimum corner lands at the requested world position.
                BlockPos worldPos = rotated.offset(anchor);

                // Clip against column bounding box.
                if (!clip.isInside(worldPos)) {
                    continue;
                }

                // Clip against the template's own transformed extent when known-shape is set.
                if (templateBox != null && !templateBox.isInside(rotated)) {
                    continue;
                }

                // Mirror then rotate, matching placeInWorld line 253 exactly.
                BlockState state = transformStates ? info.state().mirror(vanillaMirror).rotate(rotation) : info.state();

                int sectionY = worldPos.getY() >> 4;
                int flatIndex = (worldPos.getX() & 15) | ((worldPos.getZ() & 15) << 4) | ((worldPos.getY() & 15) << 8);

                consumer.accept(new Entry(localPos, worldPos, state, info.nbt(), sectionY, flatIndex));
            }
        }
    }
}
