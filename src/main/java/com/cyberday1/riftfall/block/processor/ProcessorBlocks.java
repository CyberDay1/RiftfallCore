package com.cyberday1.riftfall.block.processor;

import com.cyberday1.riftfall.block.processor.ProcessorShapes.ProcessorButtonBlock;
import com.cyberday1.riftfall.block.processor.ProcessorShapes.ProcessorGlassBlock;
import com.cyberday1.riftfall.block.processor.ProcessorShapes.ProcessorPaneBlock;
import com.cyberday1.riftfall.block.processor.ProcessorShapes.ProcessorPressurePlateBlock;
import com.cyberday1.riftfall.block.processor.ProcessorShapes.ProcessorStairBlock;
import com.cyberday1.riftfall.block.processor.ProcessorShapes.ProcessorTrapDoorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registration and reverse-lookup for the 180 processor placeholder blocks.
 *
 * <p>
 * <strong>Naming is load-bearing.</strong> Ids are built mechanically from
 * {@link ProcessorSlot#familyId()} + {@link ProcessorVariant#suffix()} precisely so no id can be
 * fat-fingered: a structure template resolves an unknown block id to {@code minecraft:air}
 * without complaint, so a one-character typo would silently punch holes in every authored room
 * rather than throwing.
 *
 * <p>
 * <strong>State definitions are load-bearing too.</strong> Every variant extends the vanilla class
 * for its shape, so builders can author orientation (stair facing/half/shape, wall connections,
 * button face, pillar axis...) on the placeholder and have the theme processor copy those exact
 * properties onto the substituted real block.
 */
public final class ProcessorBlocks {

    /** Ticks a placeholder button stays pressed. Matches vanilla stone (non-wooden) buttons. */
    private static final int BUTTON_PRESS_TICKS = 20;

    private static List<ProcessorBlockFamily> families = List.of();
    private static Map<ProcessorSlot, ProcessorBlockFamily> bySlot = Map.of();

    /**
     * Reverse map, block instance -> what it is. Built once (see {@link #buildLookup()}) and read
     * many times by the theme processor, which runs per-block during structure placement — string
     * parsing at that rate is not acceptable, hence the identity map.
     */
    private static volatile Map<Block, Placeholder> lookup;

    private ProcessorBlocks() {
    }

    /**
     * What a placeholder block is: which material slot it paints and which shape it is.
     *
     * @param slot    the material slot, 1-15
     * @param variant the shape
     */
    public record Placeholder(ProcessorSlot slot, ProcessorVariant variant) {
        /** The registry path this placeholder was registered under. */
        public String blockId() {
            return slot.blockId(variant);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------------------------------

    /**
     * Registers all 15 families x 12 variants against the given {@link DeferredRegister.Blocks}.
     * Called once, from the block-init class.
     */
    public static List<ProcessorBlockFamily> registerAll(DeferredRegister.Blocks blocks) {
        if (!families.isEmpty()) {
            throw new IllegalStateException("Processor blocks have already been registered");
        }
        List<ProcessorBlockFamily> built = new ArrayList<>(ProcessorSlot.ALL.length);
        EnumMap<ProcessorSlot, ProcessorBlockFamily> index = new EnumMap<>(ProcessorSlot.class);
        for (ProcessorSlot slot : ProcessorSlot.ALL) {
            ProcessorBlockFamily family = registerFamily(blocks, slot);
            built.add(family);
            index.put(slot, family);
        }
        families = List.copyOf(built);
        bySlot = Collections.unmodifiableMap(index);
        return families;
    }

    private static ProcessorBlockFamily registerFamily(DeferredRegister.Blocks blocks, ProcessorSlot slot) {
        EnumMap<ProcessorVariant, DeferredBlock<? extends Block>> variants = new EnumMap<>(ProcessorVariant.class);

        DeferredBlock<Block> base = blocks.register(slot.blockId(ProcessorVariant.BASE),
                () -> new Block(solidProperties()));
        variants.put(ProcessorVariant.BASE, base);

        variants.put(ProcessorVariant.SLAB, blocks.register(slot.blockId(ProcessorVariant.SLAB),
                () -> new SlabBlock(solidProperties())));

        // StairBlock needs a base state to inherit light/emission queries from; the family's own
        // full cube is the natural choice. base.get() is safe here because the supplier only runs
        // during registry fill, after the base block above has been created.
        variants.put(ProcessorVariant.STAIRS, blocks.register(slot.blockId(ProcessorVariant.STAIRS),
                () -> new ProcessorStairBlock(base.get().defaultBlockState(), solidProperties())));

        variants.put(ProcessorVariant.WALL, blocks.register(slot.blockId(ProcessorVariant.WALL),
                () -> new WallBlock(solidProperties())));

        variants.put(ProcessorVariant.FENCE, blocks.register(slot.blockId(ProcessorVariant.FENCE),
                () -> new FenceBlock(solidProperties())));

        // WoodType only supplies the open/close sounds and the (unused) sign texture; it adds no
        // blockstate properties, so facing/open/in_wall/powered still match vanilla exactly.
        variants.put(ProcessorVariant.FENCE_GATE, blocks.register(slot.blockId(ProcessorVariant.FENCE_GATE),
                () -> new FenceGateBlock(WoodType.OAK, solidProperties())));

        variants.put(ProcessorVariant.BUTTON, blocks.register(slot.blockId(ProcessorVariant.BUTTON),
                () -> new ProcessorButtonBlock(BlockSetType.STONE, BUTTON_PRESS_TICKS,
                        solidProperties().noCollission())));

        variants.put(ProcessorVariant.PRESSURE_PLATE, blocks.register(slot.blockId(ProcessorVariant.PRESSURE_PLATE),
                () -> new ProcessorPressurePlateBlock(BlockSetType.STONE,
                        solidProperties().noCollission())));

        variants.put(ProcessorVariant.TRAPDOOR, blocks.register(slot.blockId(ProcessorVariant.TRAPDOOR),
                () -> new ProcessorTrapDoorBlock(BlockSetType.STONE, solidProperties().noOcclusion())));

        variants.put(ProcessorVariant.GLASS, blocks.register(slot.blockId(ProcessorVariant.GLASS),
                () -> new ProcessorGlassBlock(glassProperties())));

        variants.put(ProcessorVariant.GLASS_PANE, blocks.register(slot.blockId(ProcessorVariant.GLASS_PANE),
                () -> new ProcessorPaneBlock(glassProperties())));

        variants.put(ProcessorVariant.DIRECTIONAL_PILLAR, blocks.register(
                slot.blockId(ProcessorVariant.DIRECTIONAL_PILLAR),
                () -> new RotatedPillarBlock(solidProperties())));

        return new ProcessorBlockFamily(slot, variants);
    }

    /**
     * Shared properties for the opaque shapes: neutral on maps, cheap to break, no tool required,
     * and — because these are build-time tools that need to preview correctly — a solid full cube
     * wherever the vanilla equivalent is one.
     *
     * <p>
     * Flammability is not registered anywhere, and NeoForge's default
     * {@code getFlammability}/{@code getFireSpreadSpeed} return 0 for a block absent from
     * {@code FireBlock}'s burn table, so these are non-flammable by construction.
     */
    private static BlockBehaviour.Properties solidProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(1.5F)
                .sound(SoundType.STONE);
    }

    /** As {@link #solidProperties()} but see-through, matching vanilla glass. */
    private static BlockBehaviour.Properties glassProperties() {
        return solidProperties()
                .sound(SoundType.GLASS)
                .noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false);
    }

    // ---------------------------------------------------------------------------------------
    // Lookup
    // ---------------------------------------------------------------------------------------

    /** Every family, slot 1 first. */
    public static List<ProcessorBlockFamily> families() {
        return families;
    }

    /** The family for one material slot. */
    public static ProcessorBlockFamily family(ProcessorSlot slot) {
        return bySlot.get(slot);
    }

    /**
     * @param slotNumber 1-based slot number as it appears in the block id
     */
    public static ProcessorBlockFamily family(int slotNumber) {
        return family(ProcessorSlot.byNumber(slotNumber));
    }

    /** Convenience for the common "give me slot N's shape S" question. */
    public static Block get(int slotNumber, ProcessorVariant variant) {
        return family(slotNumber).block(variant);
    }

    /**
     * O(1): is this block one of ours, and if so which slot and shape?
     *
     * @return the placeholder identity, or {@code null} if the block is not a processor block
     */
    public static @Nullable Placeholder identify(Block block) {
        return lookupMap().get(block);
    }

    /**
     * Builds the reverse map. Called from common setup so the first real query is already warm;
     * safe to call more than once.
     */
    public static void buildLookup() {
        lookupMap();
    }

    private static Map<Block, Placeholder> lookupMap() {
        Map<Block, Placeholder> local = lookup;
        if (local == null) {
            synchronized (ProcessorBlocks.class) {
                local = lookup;
                if (local == null) {
                    local = createLookup();
                    lookup = local;
                }
            }
        }
        return local;
    }

    private static Map<Block, Placeholder> createLookup() {
        // Identity map: Block does not override equals/hashCode, so identity semantics are what
        // we already get — this just says so out loud and skips the virtual dispatch.
        Map<Block, Placeholder> map = new IdentityHashMap<>(ProcessorSlot.ALL.length
                * ProcessorVariant.values().length);
        for (ProcessorBlockFamily family : families) {
            for (ProcessorVariant variant : ProcessorVariant.values()) {
                map.put(family.block(variant), new Placeholder(family.slot(), variant));
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
