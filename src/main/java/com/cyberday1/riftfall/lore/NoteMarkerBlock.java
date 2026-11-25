package com.cyberday1.riftfall.lore;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.Locale;

/**
 * Authoring marker for a story note: note {@code n} of the tile it sits in, on a carrier, facing the reader.
 * Tile placement swaps it for the carrier (NotePlacement). Set its state with a debug stick.
 */
public final class NoteMarkerBlock extends Block {
    public enum Carrier implements StringRepresentable {
        LECTERN, CHEST, BARREL, SIGN, STANDING_SIGN, BIRCH_SIGN, FRAME, SHELF;

        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }

        /** Signs and frames hang on the block behind them. */
        public boolean hangs() { return this == SIGN || this == BIRCH_SIGN || this == FRAME; }
    }

    public static final EnumProperty<Carrier> CARRIER = EnumProperty.create("carrier", Carrier.class);
    public static final DirectionProperty FACING = DirectionProperty.create("facing",
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP);
    public static final IntegerProperty NOTE = IntegerProperty.create("note", 1, 3);

    public NoteMarkerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CARRIER, Carrier.CHEST).setValue(FACING, Direction.NORTH).setValue(NOTE, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CARRIER, FACING, NOTE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        var facing = state.getValue(FACING);
        return facing == Direction.UP ? state : state.setValue(FACING, rotation.rotate(facing));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        var facing = state.getValue(FACING);
        return facing == Direction.UP ? state : state.setValue(FACING, mirror.mirror(facing));
    }
}
