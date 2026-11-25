package com.cyberday1.riftfall.block.marker;

import com.cyberday1.riftfall.init.RiftfallBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * The marker block classes. All but one are a plain {@link Block} plus a state definition and nothing
 * else; {@code rift_mob_spawner} is the exception, and carries the block entity that makes a rift's
 * mobs.
 *
 * <p>
 * <strong>The state definitions are the whole point.</strong> They were measured off the palettes
 * of the 634 imported templates and cross-checked against the authoring mod, because
 * {@code NbtUtils.readBlockState} silently ignores any property name the block does not declare
 * (see {@code NbtUtils#readBlockState}: {@code if (property != null)}). A property we fail to
 * declare therefore does not warn — it just quietly collapses every authored value of it to the
 * block's default. Adding a property later is cheap; discovering that 3347 chests all face north is
 * not.
 *
 * <p>
 * {@code quest_hub} has no properties at all and so needs no class here; it registers as a bare
 * {@link Block}.
 */
public final class MarkerShapes {

    private MarkerShapes() {
    }

    // -------------------------------------------------------------------------------------------
    // rift_chest — facing, type, waterlogged (3347 placements)
    // -------------------------------------------------------------------------------------------

    /**
     * Marker for {@code riftfall:rift_chest}.
     *
     * <p>
     * Carries exactly vanilla {@code ChestBlock}'s three properties but deliberately does
     * <em>not</em> extend it: {@code ChestBlock} demands a {@code BlockEntityType} supplier, and we
     * are not ready to commit to a block entity. The templates lose nothing by that — every one of
     * the 3347 authored chest payloads is an empty {@code Items: []}, and 1.21.1's
     * {@code StructureTemplate.placeInWorld} null-checks {@code level.getBlockEntity(pos)} before
     * calling {@code loadWithComponents}, so the payload is simply skipped.
     */
    public static final class RiftChestMarkerBlock extends Block implements SimpleWaterloggedBlock {

        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
        public static final EnumProperty<ChestType> TYPE = BlockStateProperties.CHEST_TYPE;
        public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

        public RiftChestMarkerBlock(BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any()
                    .setValue(FACING, Direction.NORTH)
                    .setValue(TYPE, ChestType.SINGLE)
                    .setValue(WATERLOGGED, Boolean.FALSE));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, TYPE, WATERLOGGED);
        }

        @Override
        public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
            boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
            return defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(WATERLOGGED, water);
        }

        // Without these two a rotated or mirrored structure would place every chest facing the way
        // it was authored, i.e. wrong. Block's defaults are no-ops.
        @Override
        protected BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        protected BlockState mirror(BlockState state, Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(FACING)));
        }

        @Override
        protected FluidState getFluidState(BlockState state) {
            return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
        }

        @Override
        protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
            if (state.getValue(WATERLOGGED)) {
                level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }
            return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
        }
    }

    // -------------------------------------------------------------------------------------------
    // spring_block — strength, has_item (200 placements)
    // -------------------------------------------------------------------------------------------

    /** Marker for {@code riftfall:spring_block}. */
    public static final class SpringMarkerBlock extends Block {

        /** Inclusive 0..9, matching the authored range. */
        public static final IntegerProperty STRENGTH = IntegerProperty.create("strength", 0, 9);

        /**
         * Legacy property. The authoring mod dropped {@code has_item} from this block at some point,
         * but the 634 templates were written while it still existed and 200 placements carry it —
         * so it has to stay declared or every one of them loses whatever it said. Do not delete
         * this as dead code; nothing in Riftfall reads it, and that is expected.
         */
        public static final BooleanProperty HAS_ITEM = BooleanProperty.create("has_item");

        /** Mid-range, matching the value the templates were authored against. */
        private static final int DEFAULT_STRENGTH = 5;

        public SpringMarkerBlock(BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any()
                    .setValue(STRENGTH, DEFAULT_STRENGTH)
                    .setValue(HAS_ITEM, Boolean.FALSE));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(STRENGTH, HAS_ITEM);
        }
    }

    // -------------------------------------------------------------------------------------------
    // objective_block — activated (4 placements)
    // -------------------------------------------------------------------------------------------

    /** Marker for {@code riftfall:objective_block}. */
    public static final class ObjectiveMarkerBlock extends Block {

        public static final BooleanProperty ACTIVATED = BooleanProperty.create("activated");

        public ObjectiveMarkerBlock(BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(ACTIVATED, Boolean.FALSE));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(ACTIVATED);
        }
    }

    // -------------------------------------------------------------------------------------------
    // rift_mob_spawner — ominous, rift_mob_spawner_state (4 placements)
    // -------------------------------------------------------------------------------------------

    /**
     * Marker for {@code riftfall:rift_mob_spawner}, and the one marker that does something.
     *
     * <p>
     * The block entity is the whole of that something: it waits for a player to come near, makes a
     * handful of mobs and takes the block away with it. See {@link RiftMobSpawnerBlockEntity} for why
     * it is one-shot rather than the recharging thing {@link RiftMobSpawnerState} describes.
     */
    public static final class RiftMobSpawnerMarkerBlock extends Block implements EntityBlock {

        public static final EnumProperty<RiftMobSpawnerState> SPAWNER_STATE =
                EnumProperty.create("rift_mob_spawner_state", RiftMobSpawnerState.class);
        public static final BooleanProperty OMINOUS = BlockStateProperties.OMINOUS;

        public RiftMobSpawnerMarkerBlock(BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any()
                    .setValue(SPAWNER_STATE, RiftMobSpawnerState.INACTIVE)
                    .setValue(OMINOUS, Boolean.FALSE));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(SPAWNER_STATE, OMINOUS);
        }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new RiftMobSpawnerBlockEntity(pos, state);
        }

        /**
         * Server side only. A proximity check is a question about players, which the client half of a
         * split world cannot answer for anyone but itself.
         */
        @Override
        public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                                BlockEntityType<T> type) {
            return level.isClientSide() ? null
                    : createTickerHelper(type, RiftfallBlockEntities.RIFT_MOB_SPAWNER.get(),
                            RiftMobSpawnerBlockEntity::serverTick);
        }

        /**
         * Vanilla's {@code BaseEntityBlock} helper, copied because these markers extend {@link Block}
         * rather than that class: it checks the type the level actually has against the type the
         * ticker is written for, so a block whose entity was replaced ticks nothing instead of
         * throwing.
         */
        @SuppressWarnings("unchecked")
        private static <A extends BlockEntity, E extends BlockEntity> @Nullable BlockEntityTicker<A> createTickerHelper(
                BlockEntityType<A> given, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
            return expected == given ? (BlockEntityTicker<A>) ticker : null;
        }
    }

}
