package com.cyberday1.riftfall.block.glass;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The one-way wall: a player walks in at the face it faces, out the far side, and cannot come back.
 * Everything that is not a player finds a solid cube from every side, the boss included.
 *
 * <p>
 * It exists for boss balconies. Players were dropping into the arena and then climbing back up to
 * fight from somewhere the boss could not follow; a wall across the balcony's mouth that passes one
 * way takes the climb back away without taking the drop away.
 */
public class OneWayGlassBlock extends Block {

    /**
     * The face a player may enter by, and the one face tinted green.
     *
     * <p>
     * All six, not the four horizontals: laid flat it is a floor you drop through and cannot climb
     * back up, which is the balcony problem again with gravity doing the walking.
     */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public OneWayGlassBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /**
     * Whether {@code box} may cross the block at {@code pos}, gated on the block's <em>far</em> edge
     * rather than on its centre plane.
     *
     * <p>
     * A player's box is 0.6 wide against a block 1.0 thick, so there is a moment mid-crossing when
     * the whole box is inside the block: a centre test would turn solid at exactly that moment and
     * seal them in. Measured against the far edge the block stays open for the whole crossing and
     * shuts only once the box is entirely clear of it.
     *
     * <p>
     * The blocked side needs no separate rule, because collision stops an approaching box short of
     * that same edge &mdash; so one test reads open from in front and shut from behind, with nothing
     * remembered between ticks.
     *
     * <p>
     * Static and world-free on purpose: this is the whole behaviour of the block, and the only part
     * of it a headless test can reach.
     *
     * <p>
     * Reads whichever axis the facing names, so a vertical wall needs no separate rule. A 1.8-tall box
     * is never wholly inside one, which only means the trap the far edge exists to avoid cannot arise
     * there &mdash; not that a different rule applies.
     */
    public static boolean passable(Direction facing, AABB box, BlockPos pos) {
        Direction.Axis axis = facing.getAxis();
        return facing.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? box.max(axis) > pos.get(axis)
                : box.min(axis) < pos.get(axis) + 1;
    }

    /**
     * Empty for a player who is allowed through, a full cube for everything else.
     *
     * <p>
     * A context carrying no entity &mdash; pathfinding, placement checks, the shape cache &mdash;
     * gets the cube: a mob asked to route through here has to be told there is a wall.
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player
                && passable(state.getValue(FACING), player.getBoundingBox(), pos)
                ? Shapes.empty()
                : Shapes.block();
    }

    /**
     * Hides the face two of these share, the way glass does, so a wall reads as one pane.
     *
     * <p>
     * Without it every internal boundary draws, and draws in the blocked-side red, so a wall comes out
     * ruled into a grid of red lines. Facing is not consulted because a shared face cannot be seen from
     * outside the wall whichever way either block is turned.
     */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        return adjacentState.is(this) || super.skipRendering(state, adjacentState, side);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Faces back along the axis being looked down, so the green side is the side the placer is on and
     * they can always walk into what they just placed. Looking down puts it on top, which is the
     * drop-through floor.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    // Without these two a mirrored boss room would leave its balcony wall open from the arena side,
    // which is the one direction it exists to shut. Block's defaults are no-ops.
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }
}
