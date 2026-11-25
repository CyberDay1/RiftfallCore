package com.cyberday1.riftfall.theme.family;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.processor.ProcessorVariant;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code data/<ns>/riftfall/block_families/*.json} into the live {@link BlockFamilyTable}.
 *
 * <p>
 * Three sources feed this: the file this mod's datagen bakes from vanilla's {@code BlockFamilies}
 * (shipped as {@code riftfall:vanilla}), this mod's hand-authored {@code riftfall:riftfall_extras}
 * — which carries the shapes vanilla's family model has no concept of, chiefly
 * {@code directional_pillar} — and any fragments a datapack adds to teach Riftfall about
 * <em>modded</em> families. The baked file is applied first and every other fragment afterwards in
 * id order, so a pack can override a vanilla family and the result does not depend on hash
 * iteration order.
 */
public final class BlockFamilyTableLoader extends SimpleJsonResourceReloadListener {

    /** The id the mod's own baked file is shipped under; applied before all other fragments. */
    public static final ResourceLocation BAKED_ID =
            RiftfallCore.id(BlockFamilyTable.BAKED_FILE_NAME);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    private static volatile BlockFamilyTable table = BlockFamilyTable.empty();

    /**
     * Bumped on every successful reload. Resolved themes cache derived blockstates and key that
     * cache on this, so a {@code /reload} that changes a family cannot leave stale stairs behind.
     */
    private static volatile int generation;

    public BlockFamilyTableLoader() {
        super(GSON, BlockFamilyTable.DIRECTORY);
    }

    /** The current table. Never null; {@link BlockFamilyTable#empty()} before the first reload. */
    public static BlockFamilyTable table() {
        return table;
    }

    /** @see #generation */
    public static int generation() {
        return generation;
    }

    /**
     * Parses and merges a set of family files exactly as a reload does, without needing a
     * {@link ResourceManager}.
     *
     * <p>
     * Extracted so the tests can build the shipped table from the classpath through this same code
     * path. A test that reimplemented the merge would happily pass while the real merge was broken.
     *
     * @param files file id to parsed JSON, in any order — this method imposes the load order
     */
    public static BlockFamilyTable merge(Map<ResourceLocation, JsonElement> files) {
        List<ResourceLocation> order = new ArrayList<>(files.keySet());
        order.sort(Comparator.comparing((ResourceLocation id) -> !id.equals(BAKED_ID))
                .thenComparing(ResourceLocation::toString));

        Map<ResourceLocation, Map<ProcessorVariant, ResourceLocation>> merged = new LinkedHashMap<>();
        for (ResourceLocation id : order) {
            BlockFamilyTable.FILE_CODEC.parse(JsonOps.INSTANCE, files.get(id))
                    .resultOrPartial(error -> LOGGER.error("Ignoring malformed block family file {}: {}", id, error))
                    .ifPresent(parsed -> parsed.forEach((base, variants) ->
                            merged.computeIfAbsent(base, key -> new LinkedHashMap<>()).putAll(variants)));
        }

        return BlockFamilyTable.resolve(merged, reason -> LOGGER.error("Block family table: {}", reason));
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        BlockFamilyTable loaded = merge(files);
        table = loaded;
        generation++;

        if (loaded.size() == 0) {
            // Not fatal — every variant then degrades to its material's base block — but it means
            // no stair anywhere will be a stair, which is worth shouting about.
            LOGGER.error("Block family table is EMPTY. Every derived variant will fall back to its base block. "
                    + "Was 'data/riftfall/{}/{}.json' shipped?",
                    BlockFamilyTable.DIRECTORY, BlockFamilyTable.BAKED_FILE_NAME);
        } else {
            LOGGER.info("Loaded block family table: {} base blocks from {} file(s)", loaded.size(), files.size());
        }
    }
}
