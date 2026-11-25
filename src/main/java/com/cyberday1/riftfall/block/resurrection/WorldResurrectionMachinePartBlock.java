package com.cyberday1.riftfall.block.resurrection;

import com.cyberday1.riftfall.block.holycore.HolyCoreBlock;
import com.cyberday1.riftfall.block.holycore.HolyCoreBlockEntity;
import com.cyberday1.riftfall.block.holycore.WorldCoreSocket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Eight non-ticking pedestal cells; the controller draws their continuous sculpted model. */
public class WorldResurrectionMachinePartBlock extends Block {
    public enum PartPosition implements StringRepresentable {
        NORTH_WEST("north_west", -1, -1), NORTH("north", 0, -1), NORTH_EAST("north_east", 1, -1),
        WEST("west", -1, 0), EAST("east", 1, 0),
        SOUTH_WEST("south_west", -1, 1), SOUTH("south", 0, 1), SOUTH_EAST("south_east", 1, 1);
        private final String name;
        private final int dx, dz;
        private final VoxelShape shape;
        PartPosition(String name, int dx, int dz) {
            this.name = name; this.dx = dx; this.dz = dz;
            shape = WorldCoreSocket.cell(dx, dz);
        }
        public int dx() { return dx; }
        public int dz() { return dz; }
        @Override public String getSerializedName() { return name; }
        public BlockPos center(BlockPos part) { return part.offset(-dx, 0, -dz); }
    }
    public static final EnumProperty<PartPosition> POSITION = EnumProperty.create("position", PartPosition.class);
    public WorldResurrectionMachinePartBlock(Properties properties) {
        super(properties.noLootTable().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(POSITION, PartPosition.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(POSITION); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(POSITION).shape;
    }
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos center = state.getValue(POSITION).center(pos);
        if (!level.hasChunkAt(center)) return ItemInteractionResult.FAIL;
        return HolyCoreBlock.offer(level, center, player, stack);
    }
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos center = state.getValue(POSITION).center(pos);
        return level.hasChunkAt(center) ? HolyCoreBlock.inspect(level, center, player) : InteractionResult.FAIL;
    }
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (!next.is(this) && level instanceof ServerLevel server) {
            BlockPos center = state.getValue(POSITION).center(pos);
            if (level.hasChunkAt(center) && level.getBlockEntity(center) instanceof HolyCoreBlockEntity core) core.refresh(server);
        }
    }
}
