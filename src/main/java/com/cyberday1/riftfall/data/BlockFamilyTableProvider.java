package com.cyberday1.riftfall.data;

import com.cyberday1.riftfall.block.processor.ProcessorVariant;
import com.cyberday1.riftfall.theme.family.BlockFamilyTable;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Bakes vanilla's block-family table into a shipped data resource.
 *
 * <p>
 * <strong>This class exists so that no runtime class ever names {@code net.minecraft.data}.</strong>
 * Verified against the real 1.21.1 artifacts: the bundled dedicated-server jar
 * ({@code META-INF/versions/1.21.1/server-1.21.1.jar}) contains exactly one class under
 * {@code net/minecraft/data/} — {@code Main.class}. {@link BlockFamilies} is not there. NeoForge's
 * dev environment uses the joined jar, which <em>does</em> contain it, so a runtime reference works
 * perfectly in dev and throws {@link NoClassDefFoundError} on every dedicated server.
 *
 * <p>
 * Datagen classes may safely live in {@code src/main/java} because they are only ever loaded from
 * {@code GatherDataEvent}, which never fires in production — the same arrangement the existing
 * providers in this package use.
 *
 * <p>
 * The output is written to {@code data/riftfall/riftfall/block_families/vanilla.json} — a data pack
 * path, not an assets path, because a dedicated server has no resource packs.
 */
public final class BlockFamilyTableProvider implements DataProvider {

    /**
     * Which {@link BlockFamily.Variant}s map onto our shapes. Vanilla families also describe doors,
     * signs, and material variants like chiselled/cracked/polished; those are different
     * <em>materials</em>, not different shapes of one material, so they are not ours to derive.
     */
    private static final Map<BlockFamily.Variant, ProcessorVariant> VARIANTS = buildVariantMapping();

    private final PackOutput.PathProvider path;

    public BlockFamilyTableProvider(PackOutput output) {
        this.path = output.createPathProvider(PackOutput.Target.DATA_PACK, BlockFamilyTable.DIRECTORY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        // TreeMap on both levels: datagen output is committed to the repo, so a stable key order
        // keeps the diff empty when nothing actually changed.
        Map<ResourceLocation, Map<ProcessorVariant, ResourceLocation>> families =
                new TreeMap<>(java.util.Comparator.comparing(ResourceLocation::toString));

        BlockFamilies.getAllFamilies().forEach(family -> {
            Map<ProcessorVariant, ResourceLocation> shapes = new EnumMap<>(ProcessorVariant.class);
            family.getVariants().forEach((vanillaVariant, block) -> {
                ProcessorVariant variant = VARIANTS.get(vanillaVariant);
                if (variant != null) {
                    shapes.put(variant, key(block));
                }
            });
            if (!shapes.isEmpty()) {
                families.put(key(family.getBaseBlock()), shapes);
            }
        });

        JsonObject json = BlockFamilyTable.FILE_CODEC.encodeStart(JsonOps.INSTANCE, families)
                .getOrThrow(error -> new IllegalStateException("Could not encode block family table: " + error))
                .getAsJsonObject();

        Path file = path.json(ResourceLocation.fromNamespaceAndPath("riftfall", BlockFamilyTable.BAKED_FILE_NAME));
        return DataProvider.saveStable(cache, json, file);
    }

    @Override
    public String getName() {
        return "Riftfall block family table";
    }

    private static ResourceLocation key(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    private static Map<BlockFamily.Variant, ProcessorVariant> buildVariantMapping() {
        EnumMap<BlockFamily.Variant, ProcessorVariant> map = new EnumMap<>(BlockFamily.Variant.class);
        map.put(BlockFamily.Variant.SLAB, ProcessorVariant.SLAB);
        map.put(BlockFamily.Variant.STAIRS, ProcessorVariant.STAIRS);
        map.put(BlockFamily.Variant.WALL, ProcessorVariant.WALL);
        map.put(BlockFamily.Variant.FENCE, ProcessorVariant.FENCE);
        map.put(BlockFamily.Variant.CUSTOM_FENCE, ProcessorVariant.FENCE);
        map.put(BlockFamily.Variant.FENCE_GATE, ProcessorVariant.FENCE_GATE);
        map.put(BlockFamily.Variant.CUSTOM_FENCE_GATE, ProcessorVariant.FENCE_GATE);
        map.put(BlockFamily.Variant.BUTTON, ProcessorVariant.BUTTON);
        map.put(BlockFamily.Variant.PRESSURE_PLATE, ProcessorVariant.PRESSURE_PLATE);
        map.put(BlockFamily.Variant.TRAPDOOR, ProcessorVariant.TRAPDOOR);
        // GLASS, GLASS_PANE and DIRECTIONAL_PILLAR have no BlockFamily.Variant: they are not
        // family members in vanilla's model. A theme supplies them per material through its
        // `variants` escape hatch, or they degrade through the fallback chain.
        return map;
    }
}
