package com.cyberday1.riftfall.theme;

import com.cyberday1.riftfall.block.processor.ProcessorVariant;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Defence #3 of the four-point fallback ladder: what to do when a material has no block for a
 * variant.
 *
 * <p>
 * <strong>Why this exists.</strong> {@code NbtUtils.readBlockState} resolves an unknown block id to
 * {@code minecraft:air} silently. If theme resolution ever answers "no block for this variant" and
 * we pass that through, the room grows an invisible hole. A visibly wrong wall gets reported by a
 * player in a minute; an invisible hole is a multi-hour mystery. So there is no "no block" answer —
 * every variant walks down a chain to something solid, and the chain always terminates at the
 * slot's {@link ProcessorVariant#BASE}.
 *
 * <p>
 * The chains are shape-degradation orders, not arbitrary: a missing stair reads best as a slab
 * (still half-height, still a step), a missing wall reads best as a fence (still a thin vertical
 * divider), a missing pane reads best as glass (still transparent).
 *
 * <p>
 * <strong>Universal defaults.</strong> For most shapes, degrading to the material's own base block
 * is the right answer — a missing stair should stay the room's stone, not turn into some generic
 * block from another palette. Four shapes are the exception, and the exception is functional rather
 * than cosmetic. Glass and panes are not a {@code BlockFamily} concept, so nothing derives them, and
 * degrading a window placeholder to the slot's base block turns a window into a solid wall. Plates
 * and buttons are derivable but almost never declared, and their base block is not a plainer version
 * of the shape — it is a cube where a flat mechanism belongs, so a floor plate becomes a step. All
 * four therefore have a {@linkplain #universalDefault(ProcessorVariant) universal default} consulted
 * after every material-specific lookup has missed but <em>before</em> the fall to
 * {@link ProcessorVariant#BASE}: the shape survives, at the cost of matching the material.
 *
 * <p>
 * Deliberately free of any Minecraft type so it is directly unit-testable — the universal defaults
 * are plain id strings that the caller resolves against the block registry.
 */
public final class VariantFallback {

    private static final Map<ProcessorVariant, List<ProcessorVariant>> CHAINS = buildChains();

    private static final Map<ProcessorVariant, String> UNIVERSAL_DEFAULTS = buildUniversalDefaults();

    private VariantFallback() {
    }

    /**
     * The full lookup order for a variant, starting with the variant itself and always ending with
     * {@link ProcessorVariant#BASE}.
     *
     * @return an immutable list, never empty, never containing duplicates
     */
    public static List<ProcessorVariant> chain(ProcessorVariant variant) {
        return CHAINS.get(variant);
    }

    /**
     * The block id every material falls back to for this shape when it has none of its own, or
     * {@code null} when the shape should stay in the material's own palette instead.
     *
     * <p>
     * Only glass, panes, pressure plates and buttons have one. Adding more is a deliberate act: a
     * universal default overrides the material, so it is only correct where the shape's
     * <em>function</em> — being see-through, being flat and steppable — outweighs its material.
     *
     * @return a block id such as {@code "minecraft:glass"}, or {@code null} for none
     */
    public static @Nullable String universalDefault(ProcessorVariant variant) {
        return UNIVERSAL_DEFAULTS.get(variant);
    }

    /**
     * Walks {@link #chain(ProcessorVariant)} and returns the first non-null lookup result, with no
     * universal defaults in play.
     *
     * @param variant the shape wanted
     * @param lookup  answers "does this material have a block for this shape?", null for no
     * @return the first hit, or {@code null} only if the lookup has nothing at all — including for
     *         {@link ProcessorVariant#BASE}, which callers must then treat as a broken material
     */
    public static <T> T resolve(ProcessorVariant variant, Function<ProcessorVariant, T> lookup) {
        return resolve(variant, lookup, id -> null);
    }

    /**
     * The full ladder: every material-specific step of the chain, then the universal defaults for
     * those same steps, then the material's base block.
     *
     * <p>
     * The ordering is the whole design. Material first means a theme that declared tinted glass for
     * a material still gets tinted glass in the pane slot. Universal-before-BASE means a material
     * that declared nothing at all gets a window rather than a wall. BASE last means the ladder
     * still cannot answer "nothing", which is what would become silent air downstream.
     *
     * @param variant the shape wanted
     * @param lookup  answers "does this material have a block for this shape?", null for no
     * @param byId    resolves a {@link #universalDefault(ProcessorVariant)} id, null for unknown
     * @return the first hit, or {@code null} only if nothing answered, {@code BASE} included
     */
    public static <T> T resolve(ProcessorVariant variant, Function<ProcessorVariant, T> lookup,
            Function<String, T> byId) {
        List<ProcessorVariant> chain = chain(variant);

        for (ProcessorVariant step : chain) {
            if (step == ProcessorVariant.BASE) {
                break;
            }
            T found = lookup.apply(step);
            if (found != null) {
                return found;
            }
        }
        for (ProcessorVariant step : chain) {
            if (step == ProcessorVariant.BASE) {
                break;
            }
            String id = UNIVERSAL_DEFAULTS.get(step);
            if (id != null) {
                T found = byId.apply(id);
                if (found != null) {
                    return found;
                }
            }
        }
        return lookup.apply(ProcessorVariant.BASE);
    }

    private static Map<ProcessorVariant, List<ProcessorVariant>> buildChains() {
        EnumMap<ProcessorVariant, List<ProcessorVariant>> chains = new EnumMap<>(ProcessorVariant.class);

        chains.put(ProcessorVariant.BASE, List.of(ProcessorVariant.BASE));
        chains.put(ProcessorVariant.SLAB, List.of(ProcessorVariant.SLAB, ProcessorVariant.BASE));
        chains.put(ProcessorVariant.STAIRS,
                List.of(ProcessorVariant.STAIRS, ProcessorVariant.SLAB, ProcessorVariant.BASE));
        chains.put(ProcessorVariant.WALL,
                List.of(ProcessorVariant.WALL, ProcessorVariant.FENCE, ProcessorVariant.BASE));
        chains.put(ProcessorVariant.FENCE,
                List.of(ProcessorVariant.FENCE, ProcessorVariant.WALL, ProcessorVariant.BASE));
        chains.put(ProcessorVariant.FENCE_GATE,
                List.of(ProcessorVariant.FENCE_GATE, ProcessorVariant.FENCE, ProcessorVariant.WALL,
                        ProcessorVariant.BASE));
        chains.put(ProcessorVariant.BUTTON,
                List.of(ProcessorVariant.BUTTON, ProcessorVariant.PRESSURE_PLATE, ProcessorVariant.BASE));
        chains.put(ProcessorVariant.PRESSURE_PLATE,
                List.of(ProcessorVariant.PRESSURE_PLATE, ProcessorVariant.BUTTON, ProcessorVariant.BASE));
        chains.put(ProcessorVariant.TRAPDOOR, List.of(ProcessorVariant.TRAPDOOR, ProcessorVariant.BASE));
        chains.put(ProcessorVariant.GLASS, List.of(ProcessorVariant.GLASS, ProcessorVariant.BASE));
        chains.put(ProcessorVariant.GLASS_PANE,
                List.of(ProcessorVariant.GLASS_PANE, ProcessorVariant.GLASS, ProcessorVariant.BASE));
        chains.put(ProcessorVariant.DIRECTIONAL_PILLAR,
                List.of(ProcessorVariant.DIRECTIONAL_PILLAR, ProcessorVariant.BASE));

        for (ProcessorVariant variant : ProcessorVariant.values()) {
            List<ProcessorVariant> chain = chains.get(variant);
            if (chain == null) {
                throw new IllegalStateException("No fallback chain declared for variant " + variant);
            }
            if (chain.getFirst() != variant) {
                throw new IllegalStateException("Fallback chain for " + variant + " must start with itself");
            }
            if (chain.getLast() != ProcessorVariant.BASE) {
                throw new IllegalStateException("Fallback chain for " + variant + " must terminate at BASE");
            }
        }
        return chains;
    }

    private static Map<ProcessorVariant, String> buildUniversalDefaults() {
        EnumMap<ProcessorVariant, String> defaults = new EnumMap<>(ProcessorVariant.class);
        // Vanilla has no block family that carries glass, so without these two every material that
        // does not name its own glass would answer with an opaque block and brick up its windows.
        defaults.put(ProcessorVariant.GLASS, "minecraft:glass");
        defaults.put(ProcessorVariant.GLASS_PANE, "minecraft:glass_pane");
        // And these two for the same reason: 169 of the 186 declared families carry neither, so
        // without them a plate placeholder becomes a full block standing proud of the floor.
        defaults.put(ProcessorVariant.PRESSURE_PLATE, "minecraft:stone_pressure_plate");
        defaults.put(ProcessorVariant.BUTTON, "minecraft:stone_button");
        // Everything else is deliberately absent: those shapes read correctly as the room's own
        // material and would read worse as a generic block from some other palette.
        return java.util.Collections.unmodifiableMap(defaults);
    }
}
