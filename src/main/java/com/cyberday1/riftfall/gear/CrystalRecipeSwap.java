package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.mixin.AccessorRecipeManager;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Rift crystals replace an ingredient in other mods' recipes. {@code riftfall:crystal/replace/<ns>/<path>}
 * stands in for {@code <ns>:<path>}, which is dropped here by id, so mod load order does not matter.
 * A replacement that failed to load leaves the original in place.
 */
public final class CrystalRecipeSwap {

    private static final Logger LOGGER = LogUtils.getLogger();
    static final String PREFIX = "crystal/replace/";

    private static volatile int lastReplacements = -1;
    private static volatile List<String> lastMissing = List.of();

    private CrystalRecipeSwap() {
    }

    /** Replacements loaded at the last {@link #swap}, or -1 before it ran. */
    public static int lastReplacements() {
        return lastReplacements;
    }

    /** Originals a loaded replacement named but no mod shipped: an upstream rename. */
    public static List<String> lastMissing() {
        return lastMissing;
    }

    /** The recipe a replacement id stands in for, or null if the id is not a replacement. */
    public static @Nullable ResourceLocation originalOf(ResourceLocation id) {
        if (!id.getNamespace().equals("riftfall") || !id.getPath().startsWith(PREFIX)) {
            return null;
        }
        String rest = id.getPath().substring(PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            return null;
        }
        return ResourceLocation.tryBuild(rest.substring(0, slash), rest.substring(slash + 1));
    }

    public static void swap(RecipeManager manager) {
        AccessorRecipeManager recipes = (AccessorRecipeManager) manager;
        Map<ResourceLocation, RecipeHolder<?>> byName = recipes.getByName();
        Set<ResourceLocation> drop = new TreeSet<>();
        Set<String> missing = new TreeSet<>();
        int replacements = 0;
        for (ResourceLocation id : byName.keySet()) {
            ResourceLocation original = originalOf(id);
            if (original == null) {
                continue;
            }
            replacements++;
            if (byName.containsKey(original)) {
                drop.add(original);
            } else {
                missing.add(original.toString());
            }
        }
        lastReplacements = replacements;
        lastMissing = List.copyOf(missing);
        if (!missing.isEmpty()) {
            LOGGER.warn("Crystal recipe swap: no original for {}", missing);
        }
        if (drop.isEmpty()) {
            return;
        }
        ImmutableMultimap.Builder<RecipeType<?>, RecipeHolder<?>> keptByType =
                ImmutableMultimap.builder();
        recipes.getByType().forEach((type, holder) -> {
            if (!drop.contains(holder.id())) {
                keptByType.put(type, holder);
            }
        });
        ImmutableMap.Builder<ResourceLocation, RecipeHolder<?>> keptByName = ImmutableMap.builder();
        byName.forEach((id, holder) -> {
            if (!drop.contains(id)) {
                keptByName.put(id, holder);
            }
        });
        recipes.setByType(keptByType.build());
        recipes.setByName(keptByName.build());
        LOGGER.info("Crystal recipe swap: {} originals replaced by a rift-crystal recipe", drop.size());
    }
}
