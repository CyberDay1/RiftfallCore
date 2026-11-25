package com.cyberday1.riftfall.theme;

import com.cyberday1.riftfall.block.processor.ProcessorSlot;
import com.cyberday1.riftfall.block.processor.ProcessorVariant;
import com.cyberday1.riftfall.theme.family.BlockFamilyTable;
import com.cyberday1.riftfall.worldgen.decorator.RiftDecorator;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A {@link RiftTheme} with every slot and every variant already turned into a concrete
 * {@link BlockState}, ready for the substitutor's hot path.
 *
 * <p>
 * This class is where three of the four defences against silent air live. The fourth (never
 * returning air for a placeholder that reached placement) is upheld by the processor, but only
 * because this class guarantees a non-air answer for every one of the 15x12 combinations.
 *
 * <ol>
 * <li><strong>Load-time validation.</strong> Every block id is resolved against the block registry
 * here rather than by the codec, so an unknown id is a logged error and a dropped entry instead of
 * a whole theme failing to parse. Surviving weights are renormalised, so dropping one of two 0.5
 * entries yields 1.0 rather than half a slot's worth of nothing.</li>
 * <li><strong>Empty slot.</strong> A slot with no surviving entries — including a slot the author
 * simply omitted, which is legal — uses the theme's {@code fallback} block, else
 * {@code minecraft:stone}.</li>
 * <li><strong>Missing variant.</strong> Derivation misses walk {@link VariantFallback}, which
 * terminates at the material's own base block.</li>
 * </ol>
 *
 * <p>
 * The reason all of this is belt-and-braces rather than "just validate the theme":
 * {@code NbtUtils.readBlockState} resolves an unknown block id to {@code minecraft:air} silently,
 * with no warning. Every gap here would become an invisible hole in a wall, and an invisible hole
 * is a multi-hour debugging mystery where a visibly wrong wall is a one-line bug report.
 */
public final class ResolvedTheme {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Defence #2's terminal answer when a theme names no fallback of its own. */
    public static final ResourceLocation DEFAULT_FALLBACK_BLOCK =
            ResourceLocation.withDefaultNamespace("stone");

    /**
     * One fully derived material: every shape it can be asked for, indexed by
     * {@link ProcessorVariant#ordinal()}, plus the relative weight it was declared with.
     */
    private record Material(BlockState[] states, double weight) {
    }

    private final EnumMap<ProcessorSlot, WeightedTable<Material>> slots;
    private final List<RiftDecorator> decorators;
    private final NoiseScale noiseScale;
    private final int seedAdjustment;

    private ResolvedTheme(EnumMap<ProcessorSlot, WeightedTable<Material>> slots, List<RiftDecorator> decorators,
            NoiseScale noiseScale, int seedAdjustment) {
        this.slots = slots;
        this.decorators = decorators;
        this.noiseScale = noiseScale;
        this.seedAdjustment = seedAdjustment;
    }

    // -------------------------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------------------------

    /**
     * How many materials this slot can pick between. The substitutor uses this to skip sampling
     * noise entirely for the single-material case, which is the majority of slots in practice.
     */
    public int size(ProcessorSlot slot) {
        return slots.get(slot).size();
    }

    /**
     * The real blockstate for one placeholder.
     *
     * @param sample the coherent noise value in {@code [0, 1)} selecting the material; ignored when
     *               {@link #size(ProcessorSlot)} is 1
     * @return never air, never null
     */
    public BlockState state(ProcessorSlot slot, ProcessorVariant variant, double sample) {
        WeightedTable<Material> table = slots.get(slot);
        Material material = table.size() == 1 ? table.entries().getFirst() : table.select(sample);
        return material.states()[variant.ordinal()];
    }

    public List<RiftDecorator> decorators() {
        return decorators;
    }

    public NoiseScale noiseScale() {
        return noiseScale;
    }

    public int seedAdjustment() {
        return seedAdjustment;
    }

    // -------------------------------------------------------------------------------------------
    // Resolution
    // -------------------------------------------------------------------------------------------

    /**
     * Turns a parsed theme into a resolved one, reporting and routing around every gap.
     *
     * @param themeId only used in log messages, so an operator can tell which file is wrong
     */
    public static ResolvedTheme resolve(RiftTheme theme, ResourceLocation themeId, BlockFamilyTable families) {
        Block fallbackBlock = theme.fallback()
                .map(id -> {
                    Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
                    if (block == null) {
                        LOGGER.error("Theme '{}' declares fallback block '{}', which does not exist. Using {}.",
                                themeId, id, DEFAULT_FALLBACK_BLOCK);
                    }
                    return block;
                })
                .orElse(null);
        if (fallbackBlock == null || fallbackBlock.defaultBlockState().isAir()) {
            fallbackBlock = BuiltInRegistries.BLOCK.getOptional(DEFAULT_FALLBACK_BLOCK).orElse(Blocks.STONE);
        }

        EnumMap<ProcessorSlot, WeightedTable<Material>> resolved = new EnumMap<>(ProcessorSlot.class);
        for (ProcessorSlot slot : ProcessorSlot.ALL) {
            List<ThemeMaterial> declared = theme.materials().getOrDefault(slot, List.of());
            WeightedTable<Material> table = resolveSlot(declared, themeId, slot, families);
            if (table == null) {
                if (!declared.isEmpty()) {
                    LOGGER.warn("Theme '{}' slot {}: no usable materials survived validation; using {}.",
                            themeId, slot.number(), BuiltInRegistries.BLOCK.getKey(fallbackBlock));
                }
                table = WeightedTable.single(new Material(
                        deriveVariants(fallbackBlock, Map.of(), themeId, slot, families), 1.0D));
            }
            resolved.put(slot, table);
        }
        return new ResolvedTheme(resolved, theme.decorators(), theme.noiseScale(), theme.seedAdjustment());
    }

    private static WeightedTable<Material> resolveSlot(List<ThemeMaterial> declared, ResourceLocation themeId,
            ProcessorSlot slot, BlockFamilyTable families) {
        if (declared.isEmpty()) {
            return null;
        }
        List<Material> candidates = new ArrayList<>(declared.size());
        for (ThemeMaterial material : declared) {
            Block base = BuiltInRegistries.BLOCK.getOptional(material.block()).orElse(null);
            if (base == null || base.defaultBlockState().isAir()) {
                // Defence #1. Never let this through: the block registry is defaulted, so the
                // "obvious" get() would have handed back air and cheerfully built a hole.
                LOGGER.error("Theme '{}' slot {}: block '{}' does not exist; dropping that entry and "
                        + "renormalising the remaining weights.", themeId, slot.number(), material.block());
                continue;
            }
            candidates.add(new Material(
                    deriveVariants(base, material.variants(), themeId, slot, families), material.weight()));
        }
        return candidates.isEmpty() ? null : WeightedTable.of(candidates, Material::weight);
    }

    /**
     * Resolution order for one material, exactly as specified: explicit {@code variants} override,
     * then the family table (baked vanilla plus the authored and datapack fragments), then the
     * fallback chain, then the universal default, then the base block.
     *
     * <p>
     * The first two are consulted <em>at every step of the chain</em>, not just at the variant
     * asked for. That is what lets a theme declare tinted glass once and have the pane follow it:
     * the pane's chain steps through {@code GLASS}, and the override answers there too.
     *
     * @return a 12-slot array indexed by {@link ProcessorVariant#ordinal()}, no entry ever air
     */
    private static BlockState[] deriveVariants(Block base, Map<ProcessorVariant, ResourceLocation> overrides,
            ResourceLocation themeId, ProcessorSlot slot, BlockFamilyTable families) {
        EnumMap<ProcessorVariant, Block> declared = new EnumMap<>(ProcessorVariant.class);
        overrides.forEach((variant, id) -> {
            Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (block == null || block.defaultBlockState().isAir()) {
                LOGGER.error("Theme '{}' slot {}: variant override '{}' -> '{}' does not exist; deriving instead.",
                        themeId, slot.number(), ThemeCodecs.variantName(variant), id);
                return;
            }
            declared.put(variant, block);
        });

        Function<ProcessorVariant, Block> lookup = step -> {
            Block override = declared.get(step);
            return override != null ? override : families.variant(base, step);
        };

        BlockState[] states = new BlockState[ProcessorVariant.values().length];
        for (ProcessorVariant variant : ProcessorVariant.values()) {
            // Defence #3: walk down the shape-degradation chain rather than giving up. The third
            // argument is the universal-default rung — glass and pane only — consulted once the
            // material's own shapes have all missed but before the fall to base.
            Block block = VariantFallback.resolve(variant, lookup, ResolvedTheme::universalDefaultBlock);

            if (block == null || block.defaultBlockState().isAir()) {
                block = base;
            }
            if (block.defaultBlockState().isAir()) {
                // Only reachable if a caller handed us an air base; refuse rather than emit a hole.
                block = Blocks.STONE;
            }
            states[variant.ordinal()] = block.defaultBlockState();
        }
        return states;
    }

    /**
     * Resolves a {@link VariantFallback#universalDefault(ProcessorVariant)} id against the block
     * registry. Air is refused for the same reason everywhere else here does: the block registry is
     * defaulted, so an id that has been renamed out from under us would otherwise become a hole.
     */
    private static Block universalDefaultBlock(String id) {
        Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElse(null);
        return block == null || block.defaultBlockState().isAir() ? null : block;
    }

    /**
     * The theme of absolute last resort, built in Java from vanilla blocks with no dependency on
     * the datapack registry <em>or</em> on the baked family table.
     *
     * <p>
     * Used when {@code riftfall:default} is missing — deleted by a datapack, or the registry failed
     * to load at all. Deleting every theme in the game must still produce a playable dungeon, and
     * a fallback that itself needs a file loaded is not a fallback.
     */
    public static ResolvedTheme builtin() {
        EnumMap<ProcessorVariant, Block> stone = new EnumMap<>(ProcessorVariant.class);
        stone.put(ProcessorVariant.BASE, Blocks.STONE);
        stone.put(ProcessorVariant.SLAB, Blocks.STONE_SLAB);
        stone.put(ProcessorVariant.STAIRS, Blocks.STONE_STAIRS);
        stone.put(ProcessorVariant.WALL, Blocks.COBBLESTONE_WALL);
        stone.put(ProcessorVariant.FENCE, Blocks.COBBLESTONE_WALL);
        stone.put(ProcessorVariant.FENCE_GATE, Blocks.COBBLESTONE_WALL);
        stone.put(ProcessorVariant.BUTTON, Blocks.STONE_BUTTON);
        stone.put(ProcessorVariant.PRESSURE_PLATE, Blocks.STONE_PRESSURE_PLATE);
        stone.put(ProcessorVariant.TRAPDOOR, Blocks.OAK_TRAPDOOR);
        stone.put(ProcessorVariant.GLASS, Blocks.GLASS);
        stone.put(ProcessorVariant.GLASS_PANE, Blocks.GLASS_PANE);
        stone.put(ProcessorVariant.DIRECTIONAL_PILLAR, Blocks.STONE);

        BlockState[] states = new BlockState[ProcessorVariant.values().length];
        for (ProcessorVariant variant : ProcessorVariant.values()) {
            states[variant.ordinal()] = stone.get(variant).defaultBlockState();
        }

        EnumMap<ProcessorSlot, WeightedTable<Material>> slots = new EnumMap<>(ProcessorSlot.class);
        WeightedTable<Material> table = WeightedTable.single(new Material(states, 1.0D));
        for (ProcessorSlot slot : ProcessorSlot.ALL) {
            slots.put(slot, table);
        }
        return new ResolvedTheme(slots, List.of(), NoiseScale.DEFAULT, 0);
    }
}
