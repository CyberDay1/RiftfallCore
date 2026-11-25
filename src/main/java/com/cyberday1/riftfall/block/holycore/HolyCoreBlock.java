package com.cyberday1.riftfall.block.holycore;

import com.cyberday1.riftfall.init.RiftfallBlockEntities;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class HolyCoreBlock extends BaseEntityBlock {
    public static final MapCodec<HolyCoreBlock> CODEC = simpleCodec(HolyCoreBlock::new);
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 4);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    private static final VoxelShape BASE = WorldCoreSocket.cell(0, 0);
    public HolyCoreBlock(Properties properties) {
        super(properties.strength(-1.0F, 3600000.0F).noLootTable().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(PHASE, 0).setValue(FORMED, false));
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(PHASE, FORMED); }
    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return BASE; }
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HolyCoreBlockEntity(pos, state); }
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, RiftfallBlockEntities.HOLY_CORE.get(), HolyCoreBlockEntity::tick);
    }
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return offer(level, pos, player, stack);
    }
    public static ItemInteractionResult offer(Level level, BlockPos center, Player player, ItemStack stack) {
        if (!stack.is(RiftfallItems.SHATTERED_WORLD_SHARD.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level instanceof ServerLevel server) {
            if (!(level.getBlockEntity(center) instanceof HolyCoreBlockEntity core) || !core.offer(server, player, stack)) {
                return ItemInteractionResult.FAIL;
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return inspect(level, pos, player);
    }
    public static InteractionResult inspect(Level level, BlockPos center, Player player) {
        if (level instanceof ServerLevel server && level.getBlockEntity(center) instanceof HolyCoreBlockEntity core) {
            core.inspectOrRepair(server, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
