package com.cyberday1.riftfall.gallery;

import com.cyberday1.riftfall.worldgen.level.GalleryDimension;
import net.minecraft.core.BlockPos;

/**
 * Where each piece of a gallery run goes.
 *
 * <p>
 * All of the arithmetic and none of the world, so the layout can be pinned by tests without a
 * server. {@link GalleryCommand} owns everything that touches blocks.
 *
 * <h2>Cells are uniform and generously oversized</h2>
 * Rooms are authored {@code 16n - 1} on every axis and the largest is 47, so one cell is 48 wide
 * with 16 blocks of nothing around it. Packing to each piece's own extent would fit more on screen
 * and is the wrong trade: a gallery is read by eye, and two rooms whose walls touch look like one
 * room with an odd seam through it. The gap is what makes a piece's silhouette its own.
 *
 * <p>
 * The cell does grow if some piece needs more than 48 &mdash; a datapack may add anything &mdash;
 * but it grows for the <em>whole run</em> rather than per row, because a ragged grid is exactly the
 * ambiguity the gap is there to remove.
 *
 * <h2>Runs do not share a patch of ground</h2>
 * Stamping {@code rift/room} and then {@code rift/room/chaos} over the top of it would leave the
 * tail of the larger run standing beside the smaller one, labelled and indistinguishable from it.
 * Rather than clear a 600-block square first &mdash; millions of block writes for something nobody
 * asked for &mdash; each filter gets a lane of its own, picked from the filter string's hash. Two
 * filters can collide onto one lane, and if they do the later run simply overwrites the earlier the
 * way re-running one filter does; what cannot happen is two runs half-overlapping.
 */
public record GalleryGrid(BlockPos origin, int cell, int columns, int count) {

    /** The largest authored piece is 47 on a side, so this is the floor for a cell, not a guess at one. */
    public static final int MIN_PIECE_EXTENT = 48;

    /** Empty ground between one cell's piece and the next. */
    public static final int GAP = 16;

    /**
     * The lanes filters are spread across, laid out 32 by 32 rather than in one line.
     *
     * <p>
     * 1024 of them because 256 is not enough: {@code rift/room/chaos} and {@code rift/room/portal}
     * genuinely collide modulo 256, and a collision between two of the sixteen filters the shipped
     * library actually offers is not a remote possibility to be waved at. Two dimensions rather than
     * one because a single line of 1024 lanes would put the last one two million blocks out for no
     * reason; a square keeps every gallery inside 131072 on both axes.
     *
     * <p>
     * The spacing is four times the widest grid the whole 620-piece library produces, so a run
     * cannot reach the next lane.
     */
    private static final int LANE_COLUMNS = 32;
    private static final int LANES = LANE_COLUMNS * LANE_COLUMNS;
    private static final int LANE_SPACING = 4096;

    /**
     * The grid for one run.
     *
     * @param maxExtent the largest horizontal extent among the selected pieces, already oriented
     */
    public static GalleryGrid of(String filter, int count, int maxExtent) {
        int cell = Math.max(maxExtent, MIN_PIECE_EXTENT) + GAP;
        int lane = lane(filter);
        BlockPos origin = new BlockPos(
                lane % LANE_COLUMNS * LANE_SPACING,
                GalleryDimension.BUILD_Y,
                lane / LANE_COLUMNS * LANE_SPACING);
        return new GalleryGrid(origin, cell, columnsFor(count), count);
    }

    /**
     * Roughly square, and rounded up so the last row is the short one.
     *
     * <p>
     * Square because the alternative &mdash; one long row &mdash; is a kilometre of walking for 149
     * rooms, and because a grid you can see the far corner of tells you at a glance how much library
     * there is.
     */
    public static int columnsFor(int count) {
        return count <= 0 ? 0 : (int) Math.ceil(Math.sqrt(count));
    }

    /**
     * Lane index for a filter. {@code String.hashCode} is specified by the JDK rather than
     * implementation-defined, so a filter lands on the same ground on every machine and every run,
     * which is the only property this needs.
     */
    public static int lane(String filter) {
        return Math.floorMod(filter.hashCode(), LANES);
    }

    public int rows() {
        return columns == 0 ? 0 : (count + columns - 1) / columns;
    }

    /** The minimum corner of cell {@code index}, which is where that piece's own minimum corner goes. */
    public BlockPos cellCorner(int index) {
        return new BlockPos(
                origin.getX() + (index % columns) * cell,
                origin.getY(),
                origin.getZ() + (index / columns) * cell);
    }

    /**
     * Where to put the caller: outside the minimum corner on both axes, looking down the diagonal.
     *
     * <p>
     * Outside rather than in the first cell, because arriving inside a room means seeing a wall and
     * having to walk out of it before the gallery is even visible.
     *
     * <p>
     * On the ground rather than at {@code origin.getY()}: the pieces hover clear of the floor, and
     * arriving level with their undersides means arriving in mid-air and falling.
     */
    public BlockPos viewpoint() {
        return new BlockPos(origin.getX() - GAP, GalleryDimension.FLOOR_TOP_Y + 1, origin.getZ() - GAP);
    }
}
