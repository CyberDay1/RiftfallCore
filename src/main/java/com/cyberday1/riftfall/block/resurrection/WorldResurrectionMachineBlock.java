package com.cyberday1.riftfall.block.resurrection;

import com.cyberday1.riftfall.block.holycore.HolyCoreStructure;
import com.cyberday1.riftfall.init.RiftfallBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.Nullable;

/** Structure marker and placement item. Converts once the surrounding chunks are available. */
public class WorldResurrectionMachineBlock extends BaseEntityBlock {
    public static final MapCodec<WorldResurrectionMachineBlock> CODEC = simpleCodec(WorldResurrectionMachineBlock::new);
    public WorldResurrectionMachineBlock(Properties properties) {
        super(properties.strength(-1.0F, 3600000.0F).noLootTable().pushReaction(PushReaction.BLOCK));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WorldResurrectionMachineBlockEntity(pos, state); }
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server) HolyCoreStructure.initialize(server, pos);
    }
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, RiftfallBlockEntities.WORLD_RESURRECTION_MACHINE.get(),
                WorldResurrectionMachineBlockEntity::tick);
    }
}
