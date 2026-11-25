package com.cyberday1.riftfall.block.processor;

/**
 * The twelve shapes every processor family is registered in.
 *
 * <p>
 * The {@link #suffix()} strings are <strong>load-bearing</strong>. An existing library of
 * authored structure files references these exact block ids, and
 * {@code NbtUtils.readBlockState} resolves an unknown block id to {@code minecraft:air}
 * <em>silently</em> — a typo here punches invisible holes in every room instead of erroring.
 * Do not "tidy" these strings.
 *
 * <p>
 * Declaration order is also the creative-tab order within a family.
 */
public enum ProcessorVariant {

    /** Full cube. Id is the bare family id, with no suffix. */
    BASE("", ""),
    SLAB("_slab", "Slab"),
    STAIRS("_stairs", "Stairs"),
    WALL("_wall", "Wall"),
    FENCE("_fence", "Fence"),
    FENCE_GATE("_fence_gate", "Fence Gate"),
    BUTTON("_button", "Button"),
    PRESSURE_PLATE("_pressure_plate", "Pressure Plate"),
    TRAPDOOR("_trapdoor", "Trapdoor"),
    GLASS("_glass", "Glass"),
    GLASS_PANE("_glass_pane", "Glass Pane"),
    DIRECTIONAL_PILLAR("_directional_pillar", "Directional Pillar");

    private final String suffix;
    private final String displayName;

    ProcessorVariant(String suffix, String displayName) {
        this.suffix = suffix;
        this.displayName = displayName;
    }

    /** Registry-name suffix appended to the family id, e.g. {@code "_fence_gate"}. */
    public String suffix() {
        return suffix;
    }

    /** English display fragment, e.g. {@code "Fence Gate"}. Empty for {@link #BASE}. */
    public String displayName() {
        return displayName;
    }
}
