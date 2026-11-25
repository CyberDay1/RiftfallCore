package com.cyberday1.riftfall.worldgen;

import com.cyberday1.riftfall.init.RiftfallTags;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * No modded structure generates unless the pack asked for it by name.
 *
 * <h2>One list, not one hundred and ninety-six files</h2>
 * A structure set can be emptied from a datapack &mdash; {@code {"structures": []}} is legal and
 * generates nothing &mdash; but the pack has 196 non-vanilla sets across 28 mods, and every override
 * would need Riftfall ordered after its mod in {@code neoforge.mods.toml} or it loses the race in
 * silence. The list a chunk generator actually consults is one field, so it is filtered once instead.
 *
 * <h2>Filtered where every path meets</h2>
 * {@code ChunkGeneratorStructureState} is built two ways, for normal worlds and for flat ones, and
 * both end at the same constructor. Filtering there covers both and anything a mod adds later.
 * Everything downstream &mdash; the placement table, {@code ChunkGenerator#createStructures} &mdash;
 * reads that one list, so a set removed here is a set the world has never heard of.
 *
 * <h2>What it does not touch, on purpose</h2>
 * Mods that add pieces to a <em>vanilla</em> structure's jigsaw pools own no structure set and are
 * untouched: Waystones in villages, Cataclysm's Nether fortress rooms. Riding a vanilla structure is
 * allowed, so leaving them is the intended behaviour rather than a gap.
 *
 * <h2>Failure direction</h2>
 * If {@link RiftfallTags#STRUCTURES_ALLOWED} fails to load, everything modded is stripped. That is
 * the ruling's own default, so a missing tag looks exactly like a working one &mdash; which is why
 * the kept sets are logged by name rather than counted.
 */
public final class StructureBan {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String VANILLA = "minecraft";

    /** The last thing said, so that opening a rift does not repeat it per dimension. */
    private static volatile String reported = "";

    private StructureBan() {
    }

    /** The sets a world may generate: vanilla's own, plus whatever the tag names. */
    public static List<Holder<StructureSet>> allowed(List<Holder<StructureSet>> sets) {
        List<Holder<StructureSet>> kept = new ArrayList<>();
        List<String> invited = new ArrayList<>();
        int stripped = 0;
        for (Holder<StructureSet> set : sets) {
            // The tag is asked first even though vanilla is kept regardless, so that what it says is
            // what gets logged. A tag that failed to load and a tag that named nothing present read
            // the same otherwise, and those are very different worlds.
            if (set.is(RiftfallTags.STRUCTURES_ALLOWED)) {
                kept.add(set);
                invited.add(name(set));
            } else if (vanilla(set)) {
                kept.add(set);
            } else {
                stripped++;
            }
        }
        report(stripped, invited);
        return List.copyOf(kept);
    }

    private static boolean vanilla(Holder<StructureSet> set) {
        return set.unwrapKey().map(key -> VANILLA.equals(key.location().getNamespace())).orElse(false);
    }

    private static String name(Holder<StructureSet> set) {
        return set.unwrapKey().map(ResourceKey::location).map(Object::toString).orElse("an unnamed set");
    }

    /**
     * Said once per answer rather than once per world. Every rift is a dimension and every dimension
     * builds its own state, so a line per call would be a line per portal.
     */
    private static void report(int stripped, List<String> invited) {
        String said = stripped + " stripped, invited " + invited;
        if (said.equals(reported)) {
            return;
        }
        reported = said;
        LOGGER.info("Structure ban: {} modded structure set(s) will not generate. Invited by tag: {}",
                stripped, invited.isEmpty() ? "none" : String.join(", ", invited));
    }
}
