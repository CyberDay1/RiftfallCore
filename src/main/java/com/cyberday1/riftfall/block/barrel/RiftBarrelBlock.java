package com.cyberday1.riftfall.block.barrel;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.material.FluidState;
import com.cyberday1.riftfall.tool.LootingToolEffects;
import org.jetbrains.annotations.Nullable;

/**
 * A barrel in a rift, holding a different set of items for every player who opens it.
 *
 * <p>
 * Vanilla's {@code BarrelBlock} in shape and in feel &mdash; same facing, same lid, same sounds
 * &mdash; and nothing like it in what it contains. See {@link RiftBarrelBlockEntity} for the part
 * that differs.
 *
 * <h2>The three state axes</h2>
 * {@link #FACING} is where the lid points and comes from the {@code rift_chest} marker the barrel
 * was converted from. {@link #OPEN} is the lid, which is shared: two players in the same room see
 * one lid open when either of them is inside it.
 *
 * <p>
 * {@link #LOOTED} is neither. Whether a barrel has been emptied is a fact about one <em>player</em>,
 * so it cannot live in a blockstate at all &mdash; the real answer is carried per-player in
 * {@link net.neoforged.neoforge.client.model.data.ModelData}. The property exists so that the
 * ordinary blockstate loader bakes the looted twin of every variant with the rotation that variant
 * already has, which is the one thing a side-loaded model cannot be given: those are baked
 * {@code X0_Y0} and nothing else. The server never sets it, so the states with it on exist only to
 * be baked and paired.
 */
public class RiftBarrelBlock extends BaseEntityBlock {

    public static final MapCodec<RiftBarrelBlock> CODEC = simpleCodec(properties ->
            new RiftBarrelBlock(properties, BarrelTier.SHABBY));

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    /** Client-side art only; see the class comment. Never written by anything on the server. */
    public static final BooleanProperty LOOTED = BooleanProperty.create("looted");

    private final BarrelTier tier;

    public RiftBarrelBlock(BlockBehaviour.Properties properties, BarrelTier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(OPEN, Boolean.FALSE)
                .setValue(LOOTED, Boolean.FALSE));
    }

    public BarrelTier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RiftBarrelBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        // The pick collects a personal share; it never removes this block. Keep the client block
        // entity alive as well: predicted removal loses the incoming looted-model update before
        // the server's cancelled-break acknowledgement restores the block.
        if (LootingToolEffects.personalBreak(player, state)) return false;
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof RiftBarrelBlockEntity barrel) {
            barrel.open(player);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Vanilla's lid recheck, which catches the player who logged out with the barrel open. Scheduled
     * by the block entity when somebody opens it rather than on a ticker, because a rift is mostly
     * barrels nobody is standing at.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof RiftBarrelBlockEntity barrel) {
            barrel.recheckOpen();
        }
    }

    /**
     * No {@code dropContentsOnDestroy}: the contents belong to whichever players have opened it, and
     * spilling one player's share onto the floor for everyone is the one thing this block exists to
     * prevent. Breaking a rift barrel destroys what is in it, for everybody.
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, LOOTED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }
}
