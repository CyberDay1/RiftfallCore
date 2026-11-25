package com.cyberday1.riftfall.worldgen.level;

import com.cyberday1.riftfall.worldgen.layout.RiftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the doorway where two rooms meet, as a ragged collar rather than a cut opening: the plane
 * between them resolves to a per-block coin flip between the two walls, dropping anything that is
 * not a plain solid block. Ported from Wanderers of the Rift's {@code CorridorBlender} (both mods
 * are AGPL-3.0-or-later); {@link #brick} is the same aperture put to the opposite use and is ours.
 *
 * <p>
 * Rooms are authored {@code 16n - 1} and anchored at their section box's minimum corner, so this
 * writes into the one empty plane between them and nowhere else.
 */
public final class CorridorBlender {

    /** Blocks per section edge, as a shift. */
    private static final int SECTION_SHIFT = 4;

    /**
     * The aperture. {@link #CORE_ALONG}/{@link #CORE_BELOW} is the authored doorway, a 3&times;5 hole
     * every room cuts around its ring jigsaw; the ring around it is what erodes. Not the reference
     * mod's constants: theirs is this doorway extended two blocks on the high side only, which piles
     * the whole fringe onto one corner.
     *
     * <p>
     * Sideways the aperture is fixed to the section, because rooms are packed on a horizontal plan
     * and a section face is only ever entered from one side. Vertically it is measured <em>down from
     * the ring jigsaw</em>, because a room may put its doorway anywhere up its face and the two rooms
     * either side of a seam are guaranteed to agree on where &mdash; the layout refuses to pair
     * sockets at different elevations, so there is exactly one height to cut at.
     */
    private static final int WINDOW_ALONG = 5;
    private static final int WINDOW_WIDTH = 5;
    private static final int WINDOW_HEIGHT = 7;
    private static final int WINDOW_BELOW = 2;
    private static final int CORE_ALONG = 6;
    private static final int CORE_BELOW = 1;
    private static final int CORE_WIDTH = 3;
    private static final int CORE_HEIGHT = 5;

    /** Matches the room placement: notify clients, and skip the shape-update pass. */
    private static final int PLACE_FLAGS = 18;

    /**
     * Copies one room's face into the seam: the room as it would really be built, shifted one block
     * along {@code toward} and clipped to {@code box}, so the plane that would have landed just past
     * the seam lands in it instead. The far wall is never read out of the neighbouring chunk --
     * {@code FEATURES} requires its neighbours only at {@code CARVERS}, so that chunk is usually
     * still void, and whether it is depends on which way the player walked.
     */
    @FunctionalInterface
    public interface FaceSampler {
        void copyFace(RiftPlan.PlacedRoom room, Direction toward, BoundingBox box);
    }

    private CorridorBlender() {
    }

    /**
     * Opens one join. Safe to call for a seam whose rooms are not both loaded, because nothing
     * outside {@code seam}'s own chunk column is read or written.
     */
    public static void blend(WorldGenLevel level, long riftSeed, RiftPlan.Seam seam, FaceSampler sampler) {
        Direction toLow = seam.lowToHigh().getOpposite();
        BoundingBox window = window(seam);

        // The far wall, brought into the seam so it can be read from this chunk. Done first because
        // the loop below reads it back out of the world and then overwrites every cell it wrote.
        sampler.copyFace(seam.high(), toLow, window);

        Direction.Axis axis = seam.lowToHigh().getAxis();
        RandomSource random = seamRandom(riftSeed, window);
        for (int up = 0; up < WINDOW_HEIGHT; up++) {
            for (int along = 0; along < WINDOW_WIDTH; along++) {
                BlockPos pos = cell(window, axis, along, up);
                BlockState far = level.getBlockState(pos);
                BlockState near = level.getBlockState(pos.relative(toLow));
                level.setBlock(pos, choose(near, far, seam.lowToHigh(), random, along, up), PLACE_FLAGS);
            }
        }
    }

    /**
     * Closes one archway that leads nowhere, with the wall it was cut out of. The hole is authored
     * into the template around every ring jigsaw, so an unmatched archway is a finished doorway with
     * the filler's bedrock a block behind it; trimming the socket would not help.
     *
     * <p>
     * The copy arrives with that same hole in it, so the unusable cells are filled from the usable
     * ones -- the ring of wall around the arch, and therefore what that wall is really made of.
     * Nothing happens at all if no cell is usable: that is a room whose wall is glass or a ledge,
     * and a guessed block would be worse than the bedrock already there.
     */
    public static void brick(WorldGenLevel level, long riftSeed, RiftPlan.Blank blank, FaceSampler sampler) {
        BoundingBox window = window(blank);
        Direction.Axis axis = blank.outward().getAxis();
        Direction facing = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);

        // Pushed the way the archway points, so the room's outermost plane -- the one the arch is
        // cut through -- lands in the empty plane just beyond it.
        sampler.copyFace(blank.room(), blank.outward(), window);

        BlockState[] cells = new BlockState[WINDOW_WIDTH * WINDOW_HEIGHT];
        List<BlockState> wall = new ArrayList<>();
        for (int up = 0; up < WINDOW_HEIGHT; up++) {
            for (int along = 0; along < WINDOW_WIDTH; along++) {
                BlockState state = level.getBlockState(cell(window, axis, along, up));
                cells[up * WINDOW_WIDTH + along] = state;
                if (usable(state, facing)) {
                    wall.add(state);
                }
            }
        }
        if (wall.isEmpty()) {
            return;
        }

        // Drawn per cell whether or not the cell needs it, for the reason choose() gives: what the
        // arch contains must not decide how the rest of the plane falls.
        RandomSource random = seamRandom(riftSeed, window);
        for (int up = 0; up < WINDOW_HEIGHT; up++) {
            for (int along = 0; along < WINDOW_WIDTH; along++) {
                BlockState fill = wall.get(random.nextInt(wall.size()));
                if (!usable(cells[up * WINDOW_WIDTH + along], facing)) {
                    level.setBlock(cell(window, axis, along, up), fill, PLACE_FLAGS);
                }
            }
        }
    }

    /**
     * Which of the two walls wins one block. Outside the doorway the near wall is the default and
     * the far one arrives on a coin flip, so a join leans on the room you are in and dissolves
     * toward the one you are heading for.
     *
     * <p>
     * Inside the doorway a block goes down only where <em>both</em> rooms have wall, so the opening
     * is the union of the two rooms' own openings and a blend can never be the reason a doorway is
     * shut. One plugged doorway strands a whole branch and nothing else looks wrong.
     */
    private static BlockState choose(BlockState near, BlockState far, Direction axis, RandomSource random,
                                     int along, int up) {
        boolean core = along >= CORE_ALONG - WINDOW_ALONG && along < CORE_ALONG - WINDOW_ALONG + CORE_WIDTH
                && up >= WINDOW_BELOW - CORE_BELOW && up < WINDOW_BELOW - CORE_BELOW + CORE_HEIGHT;

        // Drawn once per cell whether or not the answer needs it, so how the fringe falls does not
        // depend on what the doorway happens to contain.
        boolean flip = random.nextBoolean();
        if (core) {
            return usable(near, axis) && usable(far, axis)
                    ? (flip ? far : near)
                    : Blocks.AIR.defaultBlockState();
        }

        BlockState chosen = (flip || !usable(near, axis)) && usable(far, axis) ? far : near;
        return usable(chosen, axis) ? chosen : Blocks.AIR.defaultBlockState();
    }

    /**
     * Whether a block may stand in the seam at all. The seam is one plane thick and is seen from
     * both rooms, so a block that fills its face on one side only would read as a wall from one room
     * and a hole from the other. Block entities are refused too: nothing here carries the entity
     * across. The occlusion shape is asked against nothing, since these states are not placed yet.
     */
    private static boolean usable(BlockState state, Direction axis) {
        if (!state.canOcclude() || state.hasBlockEntity()) {
            return false;
        }
        VoxelShape shape = state.getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return Block.isFaceFull(shape, axis) == Block.isFaceFull(shape, axis.getOpposite());
    }

    /** The aperture in world blocks: one plane thick, on the seam. */
    public static BoundingBox window(RiftPlan.Seam seam) {
        return window(seam.lowSection(), seam.planeCoordinate(), seam.lowToHigh().getAxis(), seam.elevation());
    }

    /** The same aperture, for an archway that is being closed rather than opened. */
    public static BoundingBox window(RiftPlan.Blank blank) {
        return window(blank.lowSection(), blank.planeCoordinate(), blank.outward().getAxis(), blank.elevation());
    }

    /**
     * A doorway high enough up its section reaches into the one above it. That is left alone rather
     * than clamped: the aperture is a run of world blocks in one chunk column and nothing here cares
     * where the section boundaries fall, so clamping would only shorten the doorway of the room that
     * asked for a high one.
     */
    private static BoundingBox window(Vec3i section, int plane, Direction.Axis axis, int elevation) {
        int up = (section.getY() << SECTION_SHIFT) + elevation - WINDOW_BELOW;
        if (axis == Direction.Axis.X) {
            int along = (section.getZ() << SECTION_SHIFT) + WINDOW_ALONG;
            return new BoundingBox(plane, up, along,
                    plane, up + WINDOW_HEIGHT - 1, along + WINDOW_WIDTH - 1);
        }
        int along = (section.getX() << SECTION_SHIFT) + WINDOW_ALONG;
        return new BoundingBox(along, up, plane,
                along + WINDOW_WIDTH - 1, up + WINDOW_HEIGHT - 1, plane);
    }

    private static BlockPos cell(BoundingBox window, Direction.Axis axis, int along, int up) {
        return axis == Direction.Axis.X
                ? new BlockPos(window.minX(), window.minY() + up, window.minZ() + along)
                : new BlockPos(window.minX() + along, window.minY() + up, window.minZ());
    }

    /**
     * Seeded from where the seam is, not from the chunk being built: a chunk-keyed random would work
     * today and repaint every join the moment a seam was built from anywhere else. The rift seed is
     * passed in because {@code ServerLevel#getSeed} answers with the <em>server's</em> seed, which is
     * the same for every rift in a save.
     */
    private static RandomSource seamRandom(long riftSeed, BoundingBox window) {
        long mixed = riftSeed
                ^ (long) window.minX() * 341873128712L
                ^ (long) window.minY() * 132897987541L
                ^ (long) window.minZ() * 6364136223846793005L;
        return RandomSource.create(mixed);
    }
}
