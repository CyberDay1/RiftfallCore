package com.cyberday1.riftfall.home.npc.cartographer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Map;
import java.util.Optional;

/**
 * Each shipped theme's home crystal, its {@code chests/rift_theme/<theme>} table's heaviest entry (checked by a test). A theme with
 * no home crystal (cave pays all 12 equally), or whose crystal's mod is absent, attunes for shards only.
 */
public final class CartographerCrystals {
    private static final String CC = "crystalcraft_unlimited_java:";

    public static final Map<ResourceLocation, ResourceLocation> BY_THEME = Map.ofEntries(
            entry("catacombs", "onyx"), entry("celestial", "ametrine"), entry("default", "alexandrite"),
            entry("ender", "moonstone"), entry("foundry", "citrine"), entry("glacial", "ice_opal"),
            entry("infernal", "fire_opal"), entry("mycelial", "peridot"), entry("ruins", "grandidierite"),
            entry("solar", "sunstone"), entry("tidal", "aquamarine"), entry("void", "maradonyx"));

    private CartographerCrystals() {}

    private static Map.Entry<ResourceLocation, ResourceLocation> entry(String theme, String crystal) {
        return Map.entry(ResourceLocation.fromNamespaceAndPath("riftfall", theme), ResourceLocation.parse(CC + crystal));
    }

    /** Crystal ids of the themes this player's atlas has visited: Quarry swaps into them are open without a survey. */
    public static java.util.Set<ResourceLocation> charted(com.cyberday1.riftfall.home.npc.depth.ResidentProgress.Player player) {
        var out = new java.util.TreeSet<ResourceLocation>();
        for (var theme : player.atlas().keySet()) if (player.visited(theme) && BY_THEME.containsKey(theme)) out.add(BY_THEME.get(theme));
        return out;
    }

    /** The crystal a theme's attunement takes, if the theme has one and its item is registered. */
    public static Optional<Item> of(ResourceLocation theme) {
        var id = BY_THEME.get(theme);
        return id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
    }
}
