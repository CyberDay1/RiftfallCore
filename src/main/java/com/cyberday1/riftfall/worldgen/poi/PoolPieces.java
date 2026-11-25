package com.cyberday1.riftfall.worldgen.poi;

import com.cyberday1.riftfall.worldgen.space.PoolElements;
import com.cyberday1.riftfall.worldgen.space.TemplateJigsaws;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws {@link PoiRecursion.Piece}s out of the live template pools.
 *
 * <p>
 * This is the half of the furnishing pass that has to touch the registry, kept apart from
 * {@link PoiRecursion} so the recursion itself stays a function of its inputs and can be reasoned
 * about without a level.
 *
 * <h2>Fallbacks are walked here, not left to vanilla</h2>
 * Vanilla resolves a pool's fallback inside the jigsaw structure placer, which this generator does
 * not use. A pool with no elements is ordinary rather than exceptional &mdash; every
 * {@code rift/ring_*} pool is empty because nobody has authored ring pieces yet &mdash; and its
 * fallback is the terminator that seals the socket. So the chain is walked explicitly, and bounded,
 * because a datapack can point two pools at each other and a content mistake should not hang a
 * worldgen thread.
 *
 * <h2>Caching</h2>
 * A room is furnished once per chunk column it touches, so the same handful of templates is asked
 * for repeatedly. Parsing a template's jigsaw list is the expensive part and the answer never
 * changes, so it is memoised. The map is concurrent because worldgen runs several chunks at once.
 */
public final class PoolPieces implements PoiRecursion.PieceResolver {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Deep enough for pool, terminator, empty; short enough that a cycle cannot spin. */
    private static final int MAX_FALLBACKS = 8;

    private final Registry<StructureTemplatePool> pools;
    private final StructureTemplateManager templates;
    private final Map<ResourceLocation, Optional<PoiRecursion.Piece>> cache = new ConcurrentHashMap<>();

    public PoolPieces(Registry<StructureTemplatePool> pools, StructureTemplateManager templates) {
        this.pools = pools;
        this.templates = templates;
    }

    @Nullable
    @Override
    public PoiRecursion.Piece resolve(ResourceLocation pool, RandomSource random) {
        StructureTemplatePool current = pools.get(pool);
        if (current == null) {
            // Not a warning. Every pool id the library's jigsaws name is emitted by datagen, so a
            // miss here means a datapack removed one, and the socket simply goes unfilled.
            return null;
        }
        for (int step = 0; step < MAX_FALLBACKS; step++) {
            if (current.size() > 0) {
                return pieceOf(current.getRandomTemplate(random));
            }
            StructureTemplatePool fallback = current.getFallback().value();
            if (fallback == current) {
                return null;
            }
            current = fallback;
        }
        LOGGER.warn("Riftfall poi: {} still had no content after {} fallbacks, so something points at"
                + " itself in a loop", pool, MAX_FALLBACKS);
        return null;
    }

    /** A pool element as the recursion wants it: untransformed size and untransformed jigsaw list. */
    @Nullable
    private PoiRecursion.Piece pieceOf(StructurePoolElement element) {
        ResourceLocation id = PoolElements.locationOf(element);
        if (id == null) {
            return null;
        }
        return cache.computeIfAbsent(id, key -> {
            Optional<StructureTemplate> template = templates.get(key);
            if (template.isEmpty()) {
                LOGGER.warn("Riftfall poi: a pool names {} but the template is gone; leaving the"
                        + " socket unfilled", key);
                return Optional.empty();
            }
            return Optional.of(new PoiRecursion.Piece(key, template.get().getSize(),
                    TemplateJigsaws.parse(template.get())));
        }).orElse(null);
    }
}
