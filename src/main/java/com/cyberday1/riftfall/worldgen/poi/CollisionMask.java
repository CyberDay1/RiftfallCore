package com.cyberday1.riftfall.worldgen.poi;

import net.minecraft.core.Vec3i;

/**
 * A one-bit-per-block occupancy volume covering the 64&times;64&times;64 blocks a room can span,
 * used to stop props landing inside each other.
 *
 * <p>
 * Props are resolved first-come-first-served in jigsaw order: each candidate is tested against
 * everything already accepted, and on a hit it is simply not placed. The room's own geometry is
 * written into the mask before any prop is considered, so a prop cannot clip into a wall either.
 * Which prop wins a contested volume is therefore decided by list order, which is why shuffling and
 * prioritising that list is a designer knob rather than an implementation detail.
 *
 * <p>
 * Storage is 4096 {@code long}s &mdash; 32&nbsp;KB, allocated once per room. Each long holds a
 * 4&times;4&times;4 cell of blocks at one bit each, laid out {@code x | (z << 2) | (y << 4)}, and
 * the cells themselves are indexed {@code cx | (cz << 4) | (cy << 8)}. Testing a 9-block prop then
 * touches at most a handful of longs instead of 729 array slots.
 *
 * <p>
 * Technique ported from Wanderers of the Rift's {@code RiftGeneratable} collision mask (AGPL-3.0);
 * see {@code ATTRIBUTION.md}. The storage layout is theirs. Three things are deliberately different,
 * each of which is a correctness fix rather than a preference:
 *
 * <ol>
 *   <li><strong>Iteration is over the cells the box actually spans</strong>, not over the box in
 *       steps of four. Stepping the box by four and mapping each step through {@code >> 2} visits
 *       {@code ceil(size/4)} cells, but an unaligned box spans {@code ceil((offset%4 + size)/4)} of
 *       them &mdash; one more, whenever the box straddles a cell boundary. A 3-block prop at offset
 *       3 covers cells 0 and 1 and the stepped loop only ever visits cell 0, so the last two blocks
 *       are neither tested nor recorded. Every POI footprint in the library is odd (3/5/7/9/11), so
 *       this misses often rather than rarely.</li>
 *   <li><strong>Out-of-range counts as occupied.</strong> The original skips negative cells and does
 *       not bound the high side at all, where {@code cx | (cz << 4) | (cy << 8)} with {@code cx=16}
 *       silently aliases onto a different cell &mdash; a collision reported in a volume the prop
 *       never touches. Rejecting instead is also the behaviour we want on its own merits: a prop
 *       hanging outside the room's section allocation would write blocks into the neighbouring
 *       room.</li>
 *   <li>The per-axis bit masks are built by loop rather than by shifting three hand-written 64-bit
 *       constants, because the constants are unreadable and this is not a hot path relative to the
 *       block placement that follows it.</li>
 * </ol>
 */
public final class CollisionMask {

    /** Blocks per side of the volume. Three sections plus headroom, so a 48-block room fits. */
    public static final int EDGE = 64;

    /** Blocks per side of one cell; one cell is one {@code long}. */
    private static final int CELL = 4;

    private static final int CELL_SHIFT = 2;
    private static final int CELLS_PER_EDGE = EDGE / CELL;

    private final long[] cells = new long[CELLS_PER_EDGE * CELLS_PER_EDGE * CELLS_PER_EDGE];

    /**
     * Whether the box {@code [min, min + size)} overlaps anything already written, or leaves the
     * volume.
     */
    public boolean collides(Vec3i min, Vec3i size) {
        return forEachCell(min, size, true);
    }

    /** Marks the box {@code [min, min + size)} as occupied. Parts outside the volume are dropped. */
    public void write(Vec3i min, Vec3i size) {
        forEachCell(min, size, false);
    }

    /**
     * Tests and writes in one pass: writes only if nothing collided, and reports whether it did.
     *
     * <p>
     * The two-call form is the trap here. A caller that tests, recurses into the prop's own
     * children, and only then writes &mdash; which is what the reference implementation does, so
     * that a prop's sub-props are positioned against the parent's <em>pre</em>-write mask &mdash;
     * is easy to get subtly wrong by writing twice or not at all. Callers that do not need the
     * recursion window should use this.
     */
    public boolean claim(Vec3i min, Vec3i size) {
        if (collides(min, size)) {
            return false;
        }
        write(min, size);
        return true;
    }

    /** Whether one block is occupied. Out-of-range reads as occupied, matching {@link #collides}. */
    public boolean isOccupied(int x, int y, int z) {
        if (outside(x, y, z)) {
            return true;
        }
        long cell = cells[cellIndex(x >> CELL_SHIFT, y >> CELL_SHIFT, z >> CELL_SHIFT)];
        return (cell & (1L << bitIndex(x, y, z))) != 0L;
    }

    /** Blocks marked occupied. Linear in the volume, so this is for tests and diagnostics. */
    public int occupiedCount() {
        int total = 0;
        for (long cell : cells) {
            total += Long.bitCount(cell);
        }
        return total;
    }

    /**
     * The single traversal behind both public operations.
     *
     * <p>
     * Walks the cells the box overlaps, builds the box's bit pattern within each, and either tests
     * it against what is there or ORs it in. Sharing one traversal is the point: a test and a write
     * that disagree about which blocks a box covers would let two props overlap while both believe
     * they were checked.
     *
     * @param testOnly true to stop at the first overlap and report it, false to write
     * @return true if {@code testOnly} and the box overlaps something or leaves the volume
     */
    private boolean forEachCell(Vec3i min, Vec3i size, boolean testOnly) {
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            return false;
        }
        int maxX = min.getX() + size.getX() - 1;
        int maxY = min.getY() + size.getY() - 1;
        int maxZ = min.getZ() + size.getZ() - 1;
        if (testOnly && (outside(min.getX(), min.getY(), min.getZ()) || outside(maxX, maxY, maxZ))) {
            return true;
        }

        for (int cy = Math.max(min.getY(), 0) >> CELL_SHIFT; cy <= Math.min(maxY, EDGE - 1) >> CELL_SHIFT; cy++) {
            for (int cz = Math.max(min.getZ(), 0) >> CELL_SHIFT; cz <= Math.min(maxZ, EDGE - 1) >> CELL_SHIFT; cz++) {
                for (int cx = Math.max(min.getX(), 0) >> CELL_SHIFT; cx <= Math.min(maxX, EDGE - 1) >> CELL_SHIFT; cx++) {
                    long pattern = pattern(cx, cy, cz, min, maxX, maxY, maxZ);
                    int index = cellIndex(cx, cy, cz);
                    if (testOnly) {
                        if ((cells[index] & pattern) != 0L) {
                            return true;
                        }
                    } else {
                        cells[index] |= pattern;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The part of one cell that the box covers, as a 64-bit pattern.
     *
     * <p>
     * Built axis by axis: a 4-bit run along x, replicated into the z rows the box spans, replicated
     * again into the y layers. Each replication is a shift by the axis' stride in the bit layout.
     */
    private static long pattern(int cx, int cy, int cz, Vec3i min, int maxX, int maxY, int maxZ) {
        int x0 = clampToCell(min.getX() - (cx << CELL_SHIFT));
        int x1 = clampToCell(maxX - (cx << CELL_SHIFT) + 1);
        int y0 = clampToCell(min.getY() - (cy << CELL_SHIFT));
        int y1 = clampToCell(maxY - (cy << CELL_SHIFT) + 1);
        int z0 = clampToCell(min.getZ() - (cz << CELL_SHIFT));
        int z1 = clampToCell(maxZ - (cz << CELL_SHIFT) + 1);

        long row = ((1L << (x1 - x0)) - 1L) << x0;
        long layer = 0L;
        for (int z = z0; z < z1; z++) {
            layer |= row << (z << CELL_SHIFT);
        }
        long block = 0L;
        for (int y = y0; y < y1; y++) {
            block |= layer << (y << (CELL_SHIFT + CELL_SHIFT));
        }
        return block;
    }

    private static int clampToCell(int value) {
        return Math.min(Math.max(value, 0), CELL);
    }

    private static boolean outside(int x, int y, int z) {
        return x < 0 || y < 0 || z < 0 || x >= EDGE || y >= EDGE || z >= EDGE;
    }

    private static int cellIndex(int cx, int cy, int cz) {
        return cx | (cz << 4) | (cy << 8);
    }

    private static int bitIndex(int x, int y, int z) {
        return (x & 3) | ((z & 3) << 2) | ((y & 3) << 4);
    }
}
