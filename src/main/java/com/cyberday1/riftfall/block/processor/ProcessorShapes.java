package com.cyberday1.riftfall.block.processor;

import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * Thin subclasses of the vanilla shape blocks.
 *
 * <p>
 * These exist purely because the matching 1.21.1 constructors are {@code protected}
 * ({@code StairBlock}, {@code ButtonBlock}, {@code PressurePlateBlock}, {@code TrapDoorBlock},
 * {@code IronBarsBlock}, {@code HalfTransparentBlock}) and are therefore only reachable from a
 * subclass. Every one of them adds <em>zero</em> behaviour and, critically, zero blockstate
 * properties: the placeholders must carry exactly the vanilla state definition, because the theme
 * processor copies the authored properties (stair facing/half/shape, trapdoor facing/half/open,
 * button facing/face, and so on) straight onto the substituted real block.
 *
 * <p>
 * {@code SlabBlock}, {@code WallBlock}, {@code FenceBlock}, {@code FenceGateBlock},
 * {@code RotatedPillarBlock} and plain {@code Block} all have public constructors in 1.21.1 and
 * need no shim.
 */
public final class ProcessorShapes {

    private ProcessorShapes() {
    }

    public static final class ProcessorStairBlock extends StairBlock {
        public ProcessorStairBlock(BlockState baseState, BlockBehaviour.Properties properties) {
            super(baseState, properties);
        }
    }

    public static final class ProcessorButtonBlock extends ButtonBlock {
        public ProcessorButtonBlock(BlockSetType type, int ticksToStayPressed,
                BlockBehaviour.Properties properties) {
            super(type, ticksToStayPressed, properties);
        }
    }

    public static final class ProcessorPressurePlateBlock extends PressurePlateBlock {
        public ProcessorPressurePlateBlock(BlockSetType type, BlockBehaviour.Properties properties) {
            super(type, properties);
        }
    }

    public static final class ProcessorTrapDoorBlock extends TrapDoorBlock {
        public ProcessorTrapDoorBlock(BlockSetType type, BlockBehaviour.Properties properties) {
            super(type, properties);
        }
    }

    /**
     * Pane placeholder. Extends {@code IronBarsBlock} (not {@code StainedGlassPaneBlock}) so the
     * state definition is exactly north/south/east/west/waterlogged with no dye colour attached.
     */
    public static final class ProcessorPaneBlock extends IronBarsBlock {
        public ProcessorPaneBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }
    }

    /**
     * Glass placeholder. {@code TransparentBlock} is vanilla glass minus the dye/beacon-tint of
     * {@code StainedGlassBlock}: same face culling, same skylight propagation, and an entirely
     * empty state definition.
     */
    public static final class ProcessorGlassBlock extends TransparentBlock {
        public ProcessorGlassBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }
    }
}
