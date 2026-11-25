package com.cyberday1.riftfall.gear;

import java.util.Collection;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The worn-stat kind of an accessory, read from any trinket mod's slot tags, not only Curios'.
 *
 * <p>
 * {@link TrinketSlots} reads {@code curios:<slot>} because that is what Apotheosis affixes can reach. The
 * recipe ban asks a wider question: is this jewellery or gloves at all? Accessories ({@code accessories:ring}),
 * Trinkets ({@code trinkets:hand/glove}) and Aether's own {@code <mod>:accessories_<kind>} tags all say so.
 * Pure: names no mod's types.
 */
public final class WornAccessory {

    /** Slot tokens to the kind they mean. Back and charm are absent: backpacks, magnets and totems live there. */
    static final Map<String, String> KINDS = Map.ofEntries(
            Map.entry("ring", "ring"),
            Map.entry("necklace", "necklace"), Map.entry("pendant", "necklace"), Map.entry("amulet", "necklace"),
            Map.entry("bracelet", "bracelet"), Map.entry("wrist", "bracelet"), Map.entry("bracer", "bracelet"),
            Map.entry("belt", "belt"), Map.entry("waist", "belt"), Map.entry("girdle", "belt"),
            Map.entry("talisman", "talisman"),
            Map.entry("hand", "hands"), Map.entry("glove", "hands"), Map.entry("gauntlet", "hands"),
            Map.entry("cape", "cape"), Map.entry("cloak", "cape"));

    /** The namespaces whose item tags are slot lists. */
    static final java.util.Set<String> SLOT_NAMESPACES = java.util.Set.of("curios", "accessories", "trinkets");

    /** Aether's convention, also followed by Deep Aether: {@code aether:accessories_gloves}. */
    static final String MOD_SLOT_PREFIX = "accessories_";

    private WornAccessory() {
    }

    /** The first stat kind any of these tags files the item under, or null. */
    static @Nullable String kind(Collection<ResourceLocation> tags) {
        for (ResourceLocation tag : tags) {
            String kind = kind(tag);
            if (kind != null) {
                return kind;
            }
        }
        return null;
    }

    /** One tag's stat kind, or null when it is not a slot tag or names a slot with no stats. */
    static @Nullable String kind(ResourceLocation tag) {
        String path = tag.getPath();
        if (!SLOT_NAMESPACES.contains(tag.getNamespace())) {
            if (!path.startsWith(MOD_SLOT_PREFIX)) {
                return null;
            }
            path = path.substring(MOD_SLOT_PREFIX.length());
        }
        // trinkets:hand/glove is group/slot; the slot decides.
        String slot = path.substring(path.lastIndexOf('/') + 1);
        for (String token : slot.split("_")) {
            String kind = KINDS.get(token);
            if (kind == null && token.endsWith("s")) {
                kind = KINDS.get(token.substring(0, token.length() - 1));
            }
            if (kind != null) {
                return kind;
            }
        }
        return null;
    }
}
