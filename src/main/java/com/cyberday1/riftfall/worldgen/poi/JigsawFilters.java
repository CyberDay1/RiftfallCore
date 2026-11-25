package com.cyberday1.riftfall.worldgen.poi;

import com.cyberday1.riftfall.worldgen.space.JigsawInfo;
import com.cyberday1.riftfall.worldgen.space.TemplateJigsaws;
import net.minecraft.util.RandomSource;

import java.util.Collections;
import java.util.List;

/**
 * The two anchor-list rewrites the recursion cannot run correctly without.
 *
 * <p>
 * Both exist because {@link PoiRecursion} resolves anchors first-come-first-served against a shared
 * collision mask, so the <em>order and membership</em> of the list is what decides which props a
 * room ends up with. That makes this the natural home for density and variety knobs &mdash;
 * probabilistic pool swaps, guaranteeing one feature exists by moving it to the front &mdash; and
 * those belong in the datapack, not here, so they are deliberately absent until the layer that
 * would configure them exists. What is here is the minimum for the recursion to behave.
 */
public final class JigsawFilters {

    private JigsawFilters() {
    }

    /**
     * Drops the ring jigsaws.
     *
     * <p>
     * Rings are layout metadata: they told the packing pass where two rooms may abut, and by the
     * time a room is being furnished they have already done their job. Left in the list they would
     * resolve {@code rift/ring_*} against pools that have no content, fall through to the passage
     * terminator, and stamp a doorway stub in the middle of a finished room. Membership is judged
     * the same way the layout judged it &mdash; namespace plus
     * {@value TemplateJigsaws#RING_POOL_MARKER} in the pool path &mdash; so the two passes cannot
     * disagree about which jigsaw is which.
     */
    public static PoiRecursion.JigsawListProcessor stripRings(String namespace) {
        return (jigsaws, random) -> jigsaws.removeIf(jigsaw ->
                jigsaw.pool().getNamespace().equals(namespace)
                        && jigsaw.pool().getPath().contains(TemplateJigsaws.RING_POOL_MARKER));
    }

    /**
     * Shuffles the list.
     *
     * <p>
     * Without this the mask makes the room deterministic in a way that reads as a bug: the anchor
     * the builder happened to place first always wins its contested volume, so the same two props
     * fight over the same corner every seed and the same one always loses. Shuffling moves that
     * decision onto the seed.
     */
    public static PoiRecursion.JigsawListProcessor shuffle() {
        return (jigsaws, random) -> shuffleWith(jigsaws, random);
    }

    /** In-wall anchor sizes a gem vein is authored for. */
    public static final java.util.Set<Integer> GEM_VEIN_SIZES = java.util.Set.of(5, 7, 9);

    /**
     * Retargets a share of the in-wall anchors to {@code rift/poi/gem_vein/<size>}: the probabilistic
     * pool swap this class was waiting for. Each anchor rolls on a hash of {@code seed} and its own
     * position, not on the shared stream, so every other prop in the room draws exactly as before and
     * a chance of 0 changes nothing.
     */
    public static PoiRecursion.JigsawListProcessor gemVeins(double chance, long seed) {
        return (jigsaws, random) -> {
            if (chance <= 0) return;
            for (int i = 0; i < jigsaws.size(); i++) {
                JigsawInfo jigsaw = jigsaws.get(i);
                Integer size = gemVeinSize(jigsaw.pool());
                if (size == null || roll(seed, jigsaw) >= chance) continue;
                jigsaws.set(i, new JigsawInfo(jigsaw.pos(), jigsaw.state(), jigsaw.name(), jigsaw.target(),
                        gemVeinPool(size), jigsaw.finalState(), jigsaw.joint(), jigsaw.placementPriority(),
                        jigsaw.selectionPriority()));
            }
        };
    }

    /** The size of an in-wall anchor a gem vein can fill, or null. */
    @org.jetbrains.annotations.Nullable
    static Integer gemVeinSize(net.minecraft.resources.ResourceLocation pool) {
        String prefix = "rift/poi/inwall/";
        if (!"riftfall".equals(pool.getNamespace()) || !pool.getPath().startsWith(prefix)) return null;
        try {
            int size = Integer.parseInt(pool.getPath().substring(prefix.length()));
            return GEM_VEIN_SIZES.contains(size) ? size : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static net.minecraft.resources.ResourceLocation gemVeinPool(int size) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("riftfall", "rift/poi/gem_vein/" + size);
    }

    static double roll(long seed, JigsawInfo jigsaw) {
        long h = seed ^ jigsaw.pos().asLong() * 0xC2B2AE3D27D4EB4FL ^ jigsaw.pool().hashCode();
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /**
     * Fisher-Yates against {@link RandomSource} rather than
     * {@link Collections#shuffle(List, java.util.Random)}, so the whole generation pass draws from
     * one worldgen-seeded stream and a rift is reproducible from its seed.
     */
    private static void shuffleWith(List<JigsawInfo> jigsaws, RandomSource random) {
        for (int i = jigsaws.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            JigsawInfo swap = jigsaws.get(i);
            jigsaws.set(i, jigsaws.get(j));
            jigsaws.set(j, swap);
        }
    }
}
