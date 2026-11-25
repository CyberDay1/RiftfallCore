package com.cyberday1.riftfall.block.marker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A prop anchor a builder can place, in place of a hand-configured jigsaw block.
 *
 * <p>
 * It never survives generation: {@link com.cyberday1.riftfall.worldgen.processor.PoiAnchorProcessor}
 * takes it out of the placement and leaves the category's {@code final_state} behind, exactly as
 * vanilla's jigsaw replacement does for a real jigsaw. What it does before that is get read by
 * {@link com.cyberday1.riftfall.worldgen.space.TemplateJigsaws#parse} and turned into the anchor.
 *
 * <p>
 * Right-clicking cycles the footprint. There is one model per category and not one per size, so the
 * size is not visible on the block &mdash; the click reports it on the action bar instead, and F3
 * shows it as {@code size}. Five textures per category would be five pieces of art that say a
 * number, which is not worth commissioning for a block no player ever sees.
 */
public class PoiAnchorBlock extends Block {

    /** Which of the five pool footprints this anchor draws from. */
    public static final EnumProperty<PoiAnchorSize> SIZE = EnumProperty.create("size", PoiAnchorSize.class);

    private final PoiAnchorKind kind;

    public PoiAnchorBlock(PoiAnchorKind kind, BlockBehaviour.Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(SIZE, PoiAnchorSize.DEFAULT));
    }

    /** Which of the four categories this block is. */
    public PoiAnchorKind kind() {
        return kind;
    }

    /**
     * The horizontal direction this anchor was placed facing, or north for the two categories that
     * have no facing at all &mdash; where the value is unused because the joint is rollable.
     */
    public Direction facing(BlockState state) {
        return Direction.NORTH;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIZE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        PoiAnchorSize next = state.getValue(SIZE).next();
        level.setBlock(pos, state.setValue(SIZE, next), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, 1.0F);
        player.displayClientMessage(Component.literal(
                kind.displayName() + " \u2014 " + next.blocks() + "\u00d7" + next.blocks()
                        + "\u00d7" + next.blocks()), true);
        return InteractionResult.CONSUME;
    }

    /**
     * The two categories whose facing is load-bearing: {@code inwall} points out of the wall it is
     * sunk into, {@code halfway} spends it on the piece's rotation.
     *
     * <p>
     * A separate class rather than a flag, because a property has to be declared during the block
     * constructor and a field set by a subclass is not readable that early. Declaring the facing on
     * all four instead would put four dead states on the two that cannot use it.
     */
    public static class Facing extends PoiAnchorBlock {

        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

        public Facing(PoiAnchorKind kind, BlockBehaviour.Properties properties) {
            super(kind, properties);
            registerDefaultState(stateDefinition.any()
                    .setValue(SIZE, PoiAnchorSize.DEFAULT)
                    .setValue(FACING, Direction.NORTH));
        }

        @Override
        public Direction facing(BlockState state) {
            return state.getValue(FACING);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(SIZE, FACING);
        }

        /**
         * Faces the way the builder is looking rather than back at them. A wall anchor is placed
         * against the wall it belongs to, so the useful direction is the one away from the player.
         */
        @Override
        public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
        }

        // Without these two a mirrored room would place every wall anchor pointing the way it was
        // authored, i.e. into the wrong wall. Block's defaults are no-ops.
        @Override
        protected BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        protected BlockState mirror(BlockState state, Mirror mirror) {
            return rotate(state, mirror.getRotation(state.getValue(FACING)));
        }
    }
}
