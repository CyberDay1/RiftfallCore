package com.cyberday1.riftfall.block.marker;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/** Feet-position authoring marker. Supported roles become CNPC residents on unlocked home tiles. */
public final class NpcSpawnMarkerBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty ROAM_RADIUS = IntegerProperty.create("roam_radius", 0, 8);
    private final NpcRole role;

    public NpcSpawnMarkerBlock(NpcRole role, BlockBehaviour.Properties properties) {
        super(properties);
        this.role = role;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ROAM_RADIUS, 0));
    }

    public NpcRole role() { return role; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ROAM_RADIUS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hit) {
        // These are builder controls; visiting a home must never let a survival player move its NPCs.
        if (!player.isCreative() || !player.mayBuild()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            BlockState next = player.isShiftKeyDown()
                    ? state.setValue(FACING, state.getValue(FACING).getClockWise())
                    : state.cycle(ROAM_RADIUS);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            player.displayClientMessage(Component.translatable("message.riftfall.npc_spawn.configured",
                    getName(), next.getValue(FACING).getName(), next.getValue(ROAM_RADIUS)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(role.serviceTranslationKey()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.riftfall.npc_spawn.position").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.riftfall.npc_spawn.controls").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.riftfall.npc_spawn.planned").withStyle(ChatFormatting.YELLOW));
    }
}
