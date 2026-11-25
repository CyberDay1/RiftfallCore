package com.cyberday1.riftfall.block.forge;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The key forge: the bench a player forges a rift key at, and the only way to make one.
 *
 * <p>
 * It was registered until now as {@code riftfall:ability_bench}, an inert marker. That name came
 * from the Wanderers of the Rift structure import, which rewrites {@code wotr:<path>} to
 * {@code riftfall:<path>} untouched, so every block id an authored template could name had to exist
 * or resolve silently to air. Nothing in the 620 imported templates ever named this one, so renaming
 * it cost nothing &mdash; and the name it carried described that mod's ability-crafting station
 * rather than anything this block does. The art is unrelated to either: it is Unbound Ascension's
 * key infusion table.
 *
 * <h2>Why a block entity</h2>
 * Not for ticking &mdash; the forge never ticks. It is here to hold the shards a player has put in,
 * which have to outlive the screen being closed and the chunk being unloaded: somebody who loads a
 * stack of shards, walks away and comes back should find them where they left them, the way a
 * furnace's contents survive. The tier is deliberately <em>not</em> kept here; it belongs to the
 * player at the screen rather than to the bench, and lives on {@link KeyForgeMenu} for exactly as
 * long as they have it open.
 */
public class KeyForgeBlock extends BaseEntityBlock {

    public static final MapCodec<KeyForgeBlock> CODEC = simpleCodec(KeyForgeBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public KeyForgeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KeyForgeBlockEntity(pos, state);
    }

    /**
     * {@link BaseEntityBlock} renders nothing by default, on the assumption that a block with an
     * entity draws itself from a renderer. This one is an ordinary static model, so it has to say so
     * or it is invisible in world while its item model looks fine &mdash; which reads as a missing
     * texture rather than a missing override.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof MenuProvider provider) {
            player.openMenu(provider, pos);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Gives the shards back when the forge is broken.
     *
     * <p>
     * The guard on {@code state.is(newState.getBlock())} is what keeps a state change from being
     * treated as a break: turning the block to face another way replaces the state, and without it
     * the contents would spill on the floor every time.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof KeyForgeBlockEntity forge) {
                Containers.dropContents(level, pos, forge);
            }
            super.onRemove(state, level, pos, newState, moving);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /**
     * Through {@link #rotate} directly rather than {@code state.rotate}, which is the same call one
     * deprecated hop further out and warns about it.
     */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }
}
