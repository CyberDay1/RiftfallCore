package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.block.breaker.WorldBreakerBlock;
import com.cyberday1.riftfall.init.RiftfallBlocks;
import com.cyberday1.riftfall.init.RiftfallParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A rift's own way out: a rip in the air, with nothing holding it open.
 *
 * <p>
 * The entrance is a frame a player built and lit, and every rule in {@link RiftPortalBlock} about
 * losing a frame follows from that: the copper is the player's half of the bargain, and taking it
 * apart is how they take the portal back. A tear is the other direction. The rift opens it, at a
 * place the rift chooses, and there is nothing for a player to build or break &mdash; so it has no
 * frame to look for, nothing to forget when it goes, and no way to be put out by mining.
 *
 * <p>
 * That is why this is a block of its own rather than a {@link RiftPortalKind} on the pane. Almost
 * everything the two share is presentation, and all of that is inherited; almost everything they do
 * not share is the frame, and every one of those is overridden here to nothing.
 *
 * <h2>Three blocks, no model</h2>
 * A pane tiles &mdash; a 21x21 entrance is 441 copies of one texture and looks right. A tear is a
 * single shape with two torn edges, a point at each end and cracks running out into the air around
 * it, so it cannot tile and it does not stop at the blocks it occupies.
 *
 * <p>
 * So it is not drawn by a block model at all. A model bakes into the chunk mesh, which settles two
 * things at once: it can never turn to face anybody, and it may not reach past one block on each
 * side ({@code BlockElement.MIN_EXTENT}/{@code MAX_EXTENT}). The rip wants both. It is drawn instead
 * by {@code RiftTearRenderer}, off the block entity the bottom block carries, as one four-block-tall
 * picture that swings to face the camera; {@link #getRenderShape} is {@link RenderShape#INVISIBLE}
 * so nothing draws it twice. {@link #SEGMENT} survives that as which third of the rip a block is,
 * which is still how the renderer knows which one of the three is the bottom.
 *
 * <h2>Being inert</h2>
 * {@link #LIVE} is off for a tear that is only there to be looked at, which is what {@code /riftfall
 * rift tear} places. The look is the part of this that needs the most iteration and the least
 * machinery, and an operator checking how a tear reads in a lit room should not be thrown out of the
 * dungeon for walking up to it.
 */
public class RiftTearBlock extends RiftPortalBlock implements EntityBlock {

    /** Which third of the artwork this block draws. */
    public static final EnumProperty<TearSegment> SEGMENT = EnumProperty.create("segment", TearSegment.class);

    /** Whether walking into it does anything. Off is scenery, and is what the test verb places. */
    public static final BooleanProperty LIVE = BooleanProperty.create("live");

    /** How much of the block's width the mote flow uses. See {@link #across}. */
    private static final double TEAR_SPREAD = 0.95;

    /** Sparks caught per animate tick at rung zero, and how many more each rung adds. */
    private static final int CAUGHT = 2;
    private static final int CAUGHT_PER_RUNG = 1;

    /**
     * Where the vortex picks a spark up, in blocks from the rip: a floor plus a spread. Outside the
     * artwork rather than over it, so the flow reads as air being taken rather than as more rip.
     */
    private static final double CATCH_RADIUS = 0.9;
    private static final double CATCH_SPREAD = 0.7;

    /** How far a caught spark climbs or sinks per tick. Small, and either way, so the ring is not a disc. */
    private static final double CAUGHT_RISE = 0.03;

    /** Where a floor spark is born, in blocks from the column, and how high above the floor. */
    static final double BED_RADIUS = 0.3;
    static final double BED_SPREAD = 0.6;
    /**
     * The bed's inner edge round a World Breaker: just outside its dais (half a block), so no spark is
     * born inside the obsidian it is meant to be lying beside.
     */
    static final double BREAKER_BED_RADIUS = 0.55;
    private static final double BED_LIFT = 0.06;

    /** Its outward speed, in blocks per tick. The band {@code RiftGroundEmberParticle.reach} is held to. */
    static final double BED_SPEED = 0.16;
    static final double BED_SPEED_SPREAD = 0.04;

    public RiftTearBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(KIND, RiftPortalKind.BAIL)
                .setValue(INTENSITY, 0)
                .setValue(SEGMENT, TearSegment.BOTTOM)
                .setValue(LIVE, true));
    }

    /**
     * Opens a tear standing on {@code base}, and says whether there was room for one.
     *
     * <p>
     * All three blocks are checked before any of them is written, so a tear with a ceiling in the way
     * is refused rather than half placed. Refusing is the right answer for both callers: a rift that
     * cannot fit its way out wants to hear so and pick somewhere else, and an operator who aimed at a
     * wall wants to be told rather than to have two thirds of a tear appear in it.
     *
     * @param axis the axis the rip spans, so its face is the other one
     * @return false if anything solid is standing where the tear would be
     */
    public static boolean open(ServerLevel level, BlockPos base, Direction.Axis axis,
                               RiftPortalKind kind, int intensity, boolean live) {
        if (!roomFor(level, base)) {
            return false;
        }
        for (int up = 0; up < TearSegment.HEIGHT; up++) {
            level.setBlockAndUpdate(base.above(up), RiftfallBlocks.RIFT_TEAR.get().defaultBlockState()
                    .setValue(AXIS, axis)
                    .setValue(KIND, kind)
                    .setValue(INTENSITY, intensity)
                    .setValue(SEGMENT, TearSegment.atHeight(up))
                    .setValue(LIVE, live));
        }
        return true;
    }

    /**
     * Opens a tear, taking down whatever is standing in its way first.
     *
     * <p>
     * For the one caller whose position is not negotiable. The arena's author marked that column as
     * the way home, so anything a fight left in it is debris rather than geometry to route around;
     * {@link #open} stays refusable because its other callers are choosing where to put a door and
     * want to be told the spot is bad.
     */
    public static boolean clearAndOpen(ServerLevel level, BlockPos base, Direction.Axis axis,
                                       RiftPortalKind kind, int intensity, boolean live) {
        if (!roomFor(level, base)) {
            for (int up = 0; up < TearSegment.HEIGHT; up++) {
                level.destroyBlock(base.above(up), false);
            }
        }
        return open(level, base, axis, kind, intensity, live);
    }

    /** Whether a tear is standing on this block, which is what a way home is made of. */
    public static boolean standsAt(ServerLevel level, BlockPos base) {
        return level.getBlockState(base).getBlock() instanceof RiftTearBlock;
    }

    /**
     * Whether all three blocks a tear would fill are free.
     *
     * <p>
     * Split out of {@link #open} so a caller that must commit something before opening &mdash; the
     * World Breaker remembers a tier and a seed first, exactly as a key does with a frame &mdash; can
     * find out there is a ceiling in the way before it has anything to undo.
     */
    public static boolean roomFor(ServerLevel level, BlockPos base) {
        for (int up = 0; up < TearSegment.HEIGHT; up++) {
            if (!level.getBlockState(base.above(up)).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    /**
     * A key opening an entrance over a World Breaker, and everything that has to be true with it.
     *
     * <p>
     * The same four moves {@link RiftKeyItem} makes on a copper frame, in the same order and for the
     * same reasons: remember before opening, because opening is what makes it enterable and a player
     * already standing on the plate is inside it on the next tick; warm after, because the walk back
     * is the time that buys.
     *
     * <p>
     * Lives here rather than on the block because every service it touches is in this package, and a
     * plate reaching across for {@link RiftPortals} and {@link RiftWarmup} would have to widen them.
     *
     * @return false if there was no room, in which case nothing was written or remembered
     */
    public static boolean light(ServerLevel level, BlockPos breaker, Direction.Axis axis, int tier) {
        BlockPos base = breaker.above();
        if (!roomFor(level, base)) {
            return false;
        }
        int intensity = RiftPortalBlock.intensityFor(tier);
        if (!RiftWarmup.pending(level, breaker)) prepare(level, breaker, tier);
        open(level, base, axis, RiftPortalKind.ENTRANCE, intensity, true);
        flare(level, column(breaker), axis,
                RiftPortalBlock.LIGHT_PITCH - RiftPortalBlock.LIGHT_PER_RUNG * intensity);
        return true;
    }

    /** Spend the visible windup generating the same dungeon that the completed tear will use. */
    public static void prepare(ServerLevel level, BlockPos breaker, int tier) {
        long seed = level.getRandom().nextLong();
        RiftPortals.of(level).remember(breaker, tier, seed);
        RiftWarmup.open(level, breaker, tier, seed);
    }

    /**
     * A plate being broken: unsay everything keyed on it, and take its tear down with it.
     *
     * <p>
     * A tear left standing on nothing would still be a door, and one whose plate is gone has no
     * position left for {@link #getPortalDestination} to file a run under.
     */
    public static void broken(ServerLevel level, BlockPos breaker) {
        RiftPortalBlock.forgetDoor(level, breaker);
        close(level, breaker);
    }

    /**
     * Takes down the tear standing on a World Breaker, if one still is.
     *
     * <p>
     * The counterpart to {@link RiftPortalShape#douse(ServerLevel, BlockPos)} and guarded the same
     * way, so a run ending can call both and let each ignore the kind of door it is not. Quiet, so a
     * plate whose tear goes does not hear about it three times.
     */
    public static void close(ServerLevel level, BlockPos breaker) {
        BlockPos base = breaker.above();
        for (int up = 0; up < TearSegment.HEIGHT; up++) {
            BlockPos at = base.above(up);
            if (level.getBlockState(at).is(RiftfallBlocks.RIFT_TEAR.get())) {
                level.removeBlock(at, false);
            }
        }
    }

    /**
     * One entity, on the bottom block, and none on the other two.
     *
     * <p>
     * The renderer hung off it draws the whole rip in one go, so a second or third entity would only
     * buy two more chances to draw the same picture on top of itself.
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(SEGMENT) == TearSegment.BOTTOM ? new RiftTearBlockEntity(pos, state) : null;
    }

    /** Nothing here draws it: {@code RiftTearRenderer} does. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /**
     * No shape at all, so nothing outlines it and nothing can aim at it.
     *
     * <p>
     * The pane this inherits from carries the nether portal's shape &mdash; a four-pixel slab down
     * the middle of the block &mdash; which is right for a portal that fills a frame somebody built
     * and can see the edges of. A tear has no frame, and worse, the two do not even agree on where it
     * is: the picture is three blocks wide, four tall, and swings to face the camera, while the
     * blocks under it are a one-wide column that cannot turn. So the outline was never around the
     * rip, it was around a slab hidden somewhere inside it, and it moved out of the picture as the
     * player walked round.
     *
     * <p>
     * Empty costs nothing else here. Collision is already off in the block's properties, so this only
     * governs the selection box and what a ray can hit; the block is {@code strength(-1)} and has no
     * loot table, so there was never anything to gain by aiming at it. ⚠ It does <em>not</em> stop a
     * live tear taking hold of a player: {@code Entity.checkInsideBlocks} walks the block positions
     * its bounding box covers and calls {@link #entityInside} on each without ever asking for a
     * shape.
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return Shapes.empty();
    }

    /**
     * Nothing. There is no frame to have stopped being one.
     *
     * <p>
     * Deliberately not {@code super}: {@link RiftPortalBlock#updateShape} answers a neighbour change
     * by looking for a complete copper frame and putting the pane out when it finds none, which for a
     * tear is every neighbour change there will ever be. A tear placed in open air would delete
     * itself on the tick it was written.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return state;
    }

    /** Nothing, for the reason {@link RiftPortalBlock#forget} gives. */
    @Override
    protected void forget(ServerLevel level, BlockPos pos) {
    }

    /**
     * Only a live tear takes hold of what walks into it, or marks the surface behind it.
     *
     * <p>
     * Both halves are skipped together on purpose. An inert tear that still left a wake would be
     * telling the player it had noticed them, which is exactly the thing it has not done.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(LIVE)) {
            super.entityInside(state, level, pos, entity);
        }
    }

    /**
     * Both directions, told apart by which world the tear is standing in.
     *
     * <p>
     * Inside a rift it is the way out, and the run's roster knows where each player came from, so
     * there is nothing to make and nothing to spend.
     *
     * <p>
     * ⭐ Outside one, an entrance tear standing on a World Breaker is a door in &mdash; the plate is
     * the "frame", and its position is what the run is filed under. Every other kind of tear falls
     * through to null: a boss drop and an anchor's way home lead somewhere that already exists.
     */
    @Nullable
    @Override
    public DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.getValue(LIVE)) {
            return null;
        }
        if (RiftLevels.isRift(level.dimension())) {
            return entity instanceof ServerPlayer player ? RiftRuns.leaving(player) : null;
        }
        if (state.getValue(KIND) != RiftPortalKind.ENTRANCE) {
            return null;
        }
        BlockPos breaker = pos.below(state.getValue(SEGMENT).height() + 1);
        if (!level.getBlockState(breaker).is(RiftfallBlocks.WORLD_BREAKER.get())) {
            return null;
        }
        return enter(level, entity, breaker, () -> close(level, breaker),
                () -> flare(level, column(breaker), state.getValue(AXIS), SPEND_PITCH));
    }

    /** The three blocks a tear on this plate fills, which is what a flare measures itself over. */
    private static BoundingBox column(BlockPos breaker) {
        BlockPos base = breaker.above();
        return BoundingBox.fromCorners(base, base.above(TearSegment.HEIGHT - 1));
    }

    /**
     * The middle of the block rather than the whole of it.
     *
     * <p>
     * The rip is 1.37 blocks wide at its widest and a point at each end, so motes born across the
     * full width would be leaving from air either side of it. {@link #TEAR_SPREAD} used to be a
     * little wider than the artwork on purpose &mdash; the edge is torn, and a flow that stops
     * exactly at the silhouette reads as a cut-out.
     *
     * <p>
     * ⚠ It cannot be that any more, and the ceiling is not taste. The rip is wider than the block it
     * is drawn from, and a mote born outside that block is drawn inside the neighbour &mdash; which
     * for a tear set in a wall is inside the wall. So this is as wide as the flow can go, not as wide
     * as the artwork got.
     */
    @Override
    protected double across(RandomSource random) {
        return 0.5 + (random.nextDouble() - 0.5) * TEAR_SPREAD;
    }

    /**
     * The pane's flow, and a vortex around it.
     *
     * <p>
     * The inherited half is untouched: the rip still throws sparks off its face, which is what says
     * the tear is a way home and is the flow the palette was written twice to match. What is added is
     * the other half of the reading &mdash; a tear is a hole with something on the far side of it, and
     * a hole that only ever gives is a vent. Sparks picked up out of the air around it and wound in
     * are what make it take as well.
     *
     * <p>
     * Added rather than substituted for the same reason it is a separate particle type: the two flows
     * disagree about direction on purpose, and one of them is the existing look.
     *
     * <p>
     * Every one of the three blocks does the vortex, and should. The centre is the column, which all
     * three share, so what the segments vary is the height a spark is caught at &mdash; which spreads
     * the vortex up the rip for free and without any block needing to know how tall the whole thing
     * is.
     *
     * <p>
     * The floor bed is the one flow that is not like that: it is on the ground, so only the bottom
     * segment spawns it and only over a real floor. Added the same way and for the same reason as the
     * vortex &mdash; the approved look is not being substituted.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);

        double centreX = pos.getX() + 0.5;
        double centreZ = pos.getZ() + 0.5;

        int sparks = CAUGHT + CAUGHT_PER_RUNG * state.getValue(INTENSITY);
        for (int spark = 0; spark < sparks; spark++) {
            double bearing = random.nextDouble() * Math.PI * 2.0;
            double radius = CATCH_RADIUS + CATCH_SPREAD * random.nextDouble();
            level.addParticle(RiftfallParticles.RIFT_VORTEX.get(),
                    centreX + Math.cos(bearing) * radius,
                    pos.getY() + random.nextDouble(),
                    centreZ + Math.sin(bearing) * radius,
                    centreX, (random.nextDouble() - 0.5) * CAUGHT_RISE, centreZ);
        }

        if (state.getValue(SEGMENT) != TearSegment.BOTTOM || !grounded(level, pos)) {
            return;
        }
        for (int spark = 0; spark < sparks; spark++) {
            double bearing = random.nextDouble() * Math.PI * 2.0;
            double radius = (onBreaker(level, pos) ? BREAKER_BED_RADIUS : BED_RADIUS)
                    + BED_SPREAD * random.nextDouble();
            double speed = BED_SPEED + BED_SPEED_SPREAD * random.nextDouble();
            level.addParticle(RiftfallParticles.RIFT_GROUND_EMBER.get(),
                    centreX + Math.cos(bearing) * radius,
                    pos.getY() + BED_LIFT - bedDrop(level, pos),
                    centreZ + Math.sin(bearing) * radius,
                    Math.cos(bearing) * speed, 0.0, Math.sin(bearing) * speed);
        }
    }

    /**
     * Whether there is a floor under the tear's base to put an ember bed on.
     *
     * <p>
     * ⚠ Sturdiness is stricter than standable: {@code SupportType.FULL} slices the shape at the block
     * boundary, so a top slab passes and a bottom slab does not. The sparks sit at the top of this
     * block, so a half-height floor would leave them in the air regardless.
     *
     * <p>
     * A World Breaker is the one exception: the bed belongs on the floor the breaker stands on, one
     * block further down, so that is the face asked about, and {@link #bedDrop} puts the sparks there.
     *
     * @param base the bottom block of the three, which is the one standing on the floor
     */
    public static boolean grounded(BlockGetter level, BlockPos base) {
        BlockPos below = onBreaker(level, base) ? base.below(2) : base.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** Whether this tear stands on a World Breaker. */
    public static boolean onBreaker(BlockGetter level, BlockPos base) {
        return level.getBlockState(base.below()).is(RiftfallBlocks.WORLD_BREAKER.get());
    }

    /**
     * How far the rip's picture comes down from its column's floor, in blocks.
     *
     * <p>
     * Zero for every ordinary floor, because {@code RiftTearCrack.DROP} already puts the picture's
     * bottom edge on the block boundary and a full block's face IS that boundary. On a World Breaker
     * it is negative: the picture is RAISED, to {@link WorldBreakerBlock#RIP_FLOOR} px over the
     * breaker's floor, so every breathing crack clears the dais and the shards floating round it.
     *
     * <p>
     * ⚠ The rip only. The ember bed reads {@link #bedDrop}, and the ground cracks are drawn before the
     * rip's pose, so neither moves with this.
     *
     * @param base the bottom block of the three
     */
    public static float ripDrop(BlockGetter level, BlockPos base) {
        return onBreaker(level, base) ? WorldBreakerBlock.RIP_DROP : 0.0F;
    }

    /**
     * How far below the base's bottom face the ember bed lies, in blocks: zero on an ordinary floor,
     * a whole block on a World Breaker, whose floor is the one the breaker itself stands on.
     *
     * @param base the bottom block of the three
     */
    public static float bedDrop(BlockGetter level, BlockPos base) {
        return onBreaker(level, base) ? 1.0F : 0.0F;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SEGMENT, LIVE);
    }
}
