package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.init.RiftfallBlocks;
import com.cyberday1.riftfall.init.RiftfallTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A copper frame with a hole in it, found by walking outwards from one block.
 *
 * <p>
 * The rules are the nether portal's, by design: an opening between 2 and
 * 21 blocks wide and 3 and 21 tall, standing upright on one horizontal axis, framed on all four
 * sides. Corners are not checked, which is why a nether portal can be built as a ring with the
 * corners left out, and the same is true here.
 *
 * <p>
 * What is <em>not</em> the nether portal's is the frame material, which is
 * {@link RiftfallTags#PORTAL_FRAME} rather than one hardcoded block. Vanilla's own
 * {@code PortalShape} could not have been reused for that even if we wanted to: NeoForge exposes the
 * frame test as {@code IBlockStateExtension#isPortalFrame}, a per-block override, so widening it
 * would mean overriding forty vanilla copper blocks instead of writing one tag.
 *
 * <h2>What "empty" means, and why it includes our own portal</h2>
 * The interior scan accepts air and rift portal blocks. Counting existing portal blocks is what
 * separates the two questions this class answers: lighting wants a frame with a completely empty
 * interior ({@link #isEmptyFrame()}), and a portal block asking whether it should still exist wants a
 * frame whose interior is completely full ({@link #isComplete()}). One scan answers both.
 */
public final class RiftPortalShape {

    public static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 21;
    public static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 21;

    /**
     * Notify clients, skip the shape-update pass. Every writer here is filling or emptying a whole
     * opening at once, so the pass would only recompute, once per neighbour, the collapse the loop
     * is already doing.
     */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final LevelAccessor level;
    private final Direction.Axis axis;

    /** Along the opening, away from {@link #bottomLeft}. Perpendicular to {@link #axis} and horizontal. */
    private final Direction right;

    @Nullable
    private final BlockPos bottomLeft;

    private final int width;
    private final int height;
    private int portalBlocks;

    private RiftPortalShape(LevelAccessor level, BlockPos inside, Direction.Axis axis) {
        this.level = level;
        this.axis = axis;
        this.right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;

        this.bottomLeft = findBottomLeft(inside);
        if (bottomLeft == null) {
            this.width = 0;
            this.height = 0;
            return;
        }
        this.width = measureWidth();
        this.height = width == 0 ? 0 : measureHeight();
    }

    /**
     * The frame around {@code inside}, trying {@code preferred} first and then the other horizontal
     * axis.
     *
     * <p>
     * Both are tried because the caller cannot know which. A player lighting a frame clicks its inner
     * face and the axis follows from the frame, not from where they were standing.
     */
    public static Optional<RiftPortalShape> find(LevelAccessor level, BlockPos inside, Direction.Axis preferred) {
        RiftPortalShape first = new RiftPortalShape(level, inside, preferred);
        if (first.isValid()) {
            return Optional.of(first);
        }
        RiftPortalShape second = new RiftPortalShape(level, inside,
                preferred == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        return second.isValid() ? Optional.of(second) : Optional.empty();
    }

    /**
     * Walks down to the floor of the opening and then left along it, and returns the corner it
     * reaches, or null if what is underneath is not a frame at all.
     */
    @Nullable
    private BlockPos findBottomLeft(BlockPos inside) {
        BlockPos.MutableBlockPos cursor = inside.mutable();
        int floor = Math.max(level.getMinBuildHeight(), inside.getY() - MAX_HEIGHT);
        while (cursor.getY() > floor && isEmpty(level.getBlockState(cursor.below()))) {
            cursor.move(Direction.DOWN);
        }
        int distance = openingUntilFrame(cursor, right.getOpposite()) - 1;
        return distance < 0 ? null : cursor.immutable().relative(right.getOpposite(), distance);
    }

    private int measureWidth() {
        int measured = openingUntilFrame(bottomLeft, right);
        return measured >= MIN_WIDTH && measured <= MAX_WIDTH ? measured : 0;
    }

    private int measureHeight() {
        int measured = openingUntilLid();
        return measured >= MIN_HEIGHT && measured <= MAX_HEIGHT && hasLid(measured) ? measured : 0;
    }

    /**
     * How many empty blocks lie in {@code direction} before the frame closes the row off, or 0 if
     * something that is neither closes it off first.
     *
     * <p>
     * The floor is checked under every step, not just the ends. A row of air with a gap in the sill
     * beneath it is not the bottom of a portal, it is a hole in a wall, and stopping at the gap is
     * what stops a frame being found where a builder did not build one.
     */
    private int openingUntilFrame(BlockPos start, Direction direction) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int distance = 0; distance <= MAX_WIDTH; distance++) {
            cursor.set(start).move(direction, distance);
            BlockState state = level.getBlockState(cursor);
            if (!isEmpty(state)) {
                return isFrame(state) ? distance : 0;
            }
            if (!isFrame(level.getBlockState(cursor.move(Direction.DOWN)))) {
                return 0;
            }
        }
        return 0;
    }

    /** How many rows of opening stand above {@link #bottomLeft} before one is not clear. */
    private int openingUntilLid() {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int row = 0; row < MAX_HEIGHT; row++) {
            if (!isFrame(level.getBlockState(cursor.set(bottomLeft).move(Direction.UP, row).move(right, -1)))
                    || !isFrame(level.getBlockState(cursor.set(bottomLeft).move(Direction.UP, row).move(right, width)))) {
                return row;
            }
            for (int column = 0; column < width; column++) {
                BlockState state = level.getBlockState(
                        cursor.set(bottomLeft).move(Direction.UP, row).move(right, column));
                if (!isEmpty(state)) {
                    return row;
                }
                if (state.is(RiftfallBlocks.RIFT_PORTAL.get())) {
                    portalBlocks++;
                }
            }
        }
        return MAX_HEIGHT;
    }

    private boolean hasLid(int height) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int column = 0; column < width; column++) {
            if (!isFrame(level.getBlockState(cursor.set(bottomLeft).move(Direction.UP, height).move(right, column)))) {
                return false;
            }
        }
        return true;
    }

    private boolean isFrame(BlockState state) {
        return state.is(RiftfallTags.PORTAL_FRAME);
    }

    private boolean isEmpty(BlockState state) {
        return state.isAir() || state.is(RiftfallBlocks.RIFT_PORTAL.get());
    }

    /** Whether a frame of a legal size was found at all, whatever is currently inside it. */
    public boolean isValid() {
        return bottomLeft != null && width >= MIN_WIDTH && width <= MAX_WIDTH
                && height >= MIN_HEIGHT && height <= MAX_HEIGHT;
    }

    /** A frame that could be lit: legal, and with nothing in it yet. */
    public boolean isEmptyFrame() {
        return isValid() && portalBlocks == 0;
    }

    /** A frame that is already lit all the way across, which is what a live portal block sits in. */
    public boolean isComplete() {
        return isValid() && portalBlocks == width * height;
    }

    public Direction.Axis axis() {
        return axis;
    }

    /**
     * The minimum corner of the opening &mdash; low on the axis the opening runs along, and on its
     * bottom row.
     *
     * <p>
     * This is how a frame is named. Every block of a portal finds the same corner from the same scan,
     * so it is the one coordinate that identifies the frame rather than the block a player happened
     * to walk into, which is what {@link RiftPortals} keys a tier on.
     *
     * <p>
     * Only meaningful once {@link #isValid()}; an invalid shape has no corner to speak of.
     */
    public BlockPos bottomLeft() {
        return bottomLeft;
    }

    /** The opposite corner of the opening from {@link #bottomLeft()}: top row, far end. */
    private BlockPos topRight() {
        return bottomLeft.relative(Direction.UP, height - 1).relative(right, width - 1);
    }

    /** The opening, in blocks — the portal itself, not the frame around it. */
    public BoundingBox area() {
        return BoundingBox.fromCorners(bottomLeft, topRight());
    }

    /**
     * Fills the opening with portal blocks. Notifies clients and skips the shape-update pass, as
     * vanilla does.
     *
     * <p>
     * Always an entrance: lighting is what a player does with a key, and a player never lights a way
     * home. The tier comes in because the client cannot go and look it up &mdash; see
     * {@link RiftPortalBlock#INTENSITY} &mdash; so it is bucketed into the blockstate here, at the one
     * moment the tier and the blocks are in the same place.
     */
    public void light(int tier) {
        light(RiftPortalKind.ENTRANCE, tier);
    }

    private void light(RiftPortalKind kind, int tier) {
        BlockState portal = RiftfallBlocks.RIFT_PORTAL.get().defaultBlockState()
                .setValue(RiftPortalBlock.AXIS, axis)
                .setValue(RiftPortalBlock.KIND, kind)
                .setValue(RiftPortalBlock.INTENSITY, RiftPortalBlock.intensityFor(tier));
        BlockPos.betweenClosed(bottomLeft, topRight()).forEach(pos -> level.setBlock(pos, portal, QUIET));
    }

    /**
     * Empties the opening, which is what putting a frame out means: the copper is untouched and the
     * tier is forgotten, so the frame is exactly what it was before anybody lit it.
     *
     * <p>
     * {@link #QUIET}, as everywhere else here. What those flags do not skip is {@code onRemove},
     * which the chunk runs whatever they say, and that is what tells {@link RiftPortals} and
     * {@link RiftRuns} the frame has gone.
     */
    public void douse() {
        BlockPos.betweenClosed(bottomLeft, topRight())
                .forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET));
    }

    /**
     * Puts out the frame at a corner, if there is still a lit one there.
     *
     * <p>
     * Takes a corner rather than a shape because the caller that needs it most &mdash; a run ending
     * &mdash; is nowhere near the frame and has only ever known it by name. Reading the block is a
     * synchronous chunk load if nobody is standing near the portal, which is the common case for a
     * party that died somewhere else; that is accepted rather than deferred, because it is one chunk
     * once per run and the alternative is a frame left lit until somebody happens to walk past it.
     */
    public static void douse(ServerLevel level, BlockPos corner) {
        BlockState state = level.getBlockState(corner);
        if (!state.is(RiftfallBlocks.RIFT_PORTAL.get())) {
            return;
        }
        find(level, corner, state.getValue(RiftPortalBlock.AXIS))
                .filter(RiftPortalShape::isComplete)
                .ifPresent(RiftPortalShape::douse);
    }
}
