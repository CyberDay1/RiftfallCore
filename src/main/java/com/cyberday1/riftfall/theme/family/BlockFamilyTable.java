package com.cyberday1.riftfall.theme.family;

import com.cyberday1.riftfall.block.processor.ProcessorVariant;
import com.cyberday1.riftfall.theme.ThemeCodecs;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * "Given {@code minecraft:deepslate_bricks}, what is its stairs block?" — answered from a table
 * baked at datagen instead of derived from the block id.
 *
 * <p>
 * <strong>Why baked, not pattern-matched.</strong> Vanilla's naming is irregular enough that no
 * rule survives contact: {@code bricks} -> {@code brick_slab} (singular), {@code oak_planks} ->
 * {@code oak_slab} (suffix dropped entirely), {@code quartz_block} -> {@code quartz_slab}, and
 * {@code stone} has no wall at all. Vanilla already holds the right answers in
 * {@code net.minecraft.data.BlockFamilies}.
 *
 * <p>
 * <strong>Why not just read {@code BlockFamilies} at runtime.</strong> Verified against the real
 * 1.21.1 artifacts: the bundled dedicated-server jar ({@code META-INF/versions/1.21.1/server-1.21.1.jar})
 * contains exactly one class under {@code net/minecraft/data/} — {@code Main.class}. NeoForge's dev
 * environment uses the joined jar, which <em>does</em> contain {@code BlockFamilies}, so a runtime
 * reference works perfectly in dev and throws {@link NoClassDefFoundError} on every dedicated
 * server. The table is therefore harvested at datagen (where the class is present) and shipped as a
 * data resource; nothing in this package names {@code net.minecraft.data}.
 *
 * <p>
 * {@link ProcessorVariant#GLASS}, {@link ProcessorVariant#GLASS_PANE} and
 * {@link ProcessorVariant#DIRECTIONAL_PILLAR} are <em>not</em> block-family concepts, so vanilla's
 * table cannot describe them and the baked file never will. They come from the hand-authored
 * fragment shipped alongside it ({@code riftfall_extras.json}), which is the same mechanism a
 * datapack uses to teach Riftfall about modded families. Anything that fragment does not cover
 * falls to {@code VariantFallback}'s universal default (glass and pane) and finally to the
 * material's base block.
 */
public final class BlockFamilyTable {

    /**
     * On-disk shape: {@code { "<base block id>": { "slab": "<id>", "stairs": "<id>", ... } }}.
     * Shared by the datagen provider and the runtime loader so the two cannot drift.
     */
    public static final Codec<Map<ResourceLocation, Map<ProcessorVariant, ResourceLocation>>> FILE_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC,
                    Codec.unboundedMap(ThemeCodecs.VARIANT, ResourceLocation.CODEC));

    /** Datapack directory scanned for family fragments: {@code data/<ns>/riftfall/block_families/}. */
    public static final String DIRECTORY = "riftfall/block_families";

    /** The file this mod's datagen writes; applied first so datapack fragments can override it. */
    public static final String BAKED_FILE_NAME = "vanilla";

    private static final BlockFamilyTable EMPTY = new BlockFamilyTable(Map.of());

    private final Map<Block, Map<ProcessorVariant, Block>> byBase;

    private BlockFamilyTable(Map<Block, Map<ProcessorVariant, Block>> byBase) {
        this.byBase = byBase;
    }

    /** A table with no families at all. Every lookup misses and the fallback chain takes over. */
    public static BlockFamilyTable empty() {
        return EMPTY;
    }

    /**
     * Resolves the id-keyed on-disk shape against the live block registry.
     *
     * <p>
     * Unknown ids are dropped with a report rather than resolved: {@code BuiltInRegistries.BLOCK}
     * is a <em>defaulted</em> registry, so {@code get(unknownId)} answers {@code minecraft:air} —
     * baking that into the table would put air behind every stair in the theme that used it.
     *
     * @param report called with a human-readable reason for each dropped id
     */
    public static BlockFamilyTable resolve(Map<ResourceLocation, Map<ProcessorVariant, ResourceLocation>> raw,
            java.util.function.Consumer<String> report) {
        Map<Block, Map<ProcessorVariant, Block>> resolved = new IdentityHashMap<>(raw.size());
        for (Map.Entry<ResourceLocation, Map<ProcessorVariant, ResourceLocation>> family : raw.entrySet()) {
            Block base = BuiltInRegistries.BLOCK.getOptional(family.getKey()).orElse(null);
            if (base == null) {
                report.accept("unknown base block '" + family.getKey() + "'");
                continue;
            }
            EnumMap<ProcessorVariant, Block> variants = new EnumMap<>(ProcessorVariant.class);
            for (Map.Entry<ProcessorVariant, ResourceLocation> variant : family.getValue().entrySet()) {
                if (variant.getKey() == ProcessorVariant.BASE) {
                    continue;
                }
                Block block = BuiltInRegistries.BLOCK.getOptional(variant.getValue()).orElse(null);
                if (block == null) {
                    report.accept("unknown block '" + variant.getValue() + "' for "
                            + ThemeCodecs.variantName(variant.getKey()) + " of '" + family.getKey() + "'");
                    continue;
                }
                variants.put(variant.getKey(), block);
            }
            // A later fragment describing the same base merges into the earlier one rather than
            // replacing it, so a pack can add just the pillar without restating the whole family.
            resolved.merge(base, Collections.unmodifiableMap(variants), (existing, added) -> {
                EnumMap<ProcessorVariant, Block> merged = new EnumMap<>(ProcessorVariant.class);
                merged.putAll(existing);
                merged.putAll(added);
                return Collections.unmodifiableMap(merged);
            });
        }
        return new BlockFamilyTable(Collections.unmodifiableMap(resolved));
    }

    /**
     * The block for one shape of one material.
     *
     * @return {@code null} if this material has no such shape — the caller walks
     *         {@code VariantFallback} rather than treating null as air
     */
    public @Nullable Block variant(Block base, ProcessorVariant variant) {
        if (variant == ProcessorVariant.BASE) {
            return base;
        }
        Map<ProcessorVariant, Block> family = byBase.get(base);
        return family == null ? null : family.get(variant);
    }

    /** Number of base blocks described. Logged at load so a silently empty table is visible. */
    public int size() {
        return byBase.size();
    }
}
