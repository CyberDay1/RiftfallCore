package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.init.RiftfallTags;
import com.cyberday1.riftfall.mixin.AccessorRecipeManager;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Modded gear comes out of a rift or it does not come at all.
 *
 * <h2>One rule, not five thousand files</h2>
 * The obvious way to do this is a datapack: ship an empty override of every recipe that makes a
 * piece of modded gear. Counted against the pack, that is 1,696 armour recipes alone and several
 * thousand once weapons, tools and trinkets are added, and each one needs Riftfall ordered after the
 * mod it overrides in {@code neoforge.mods.toml} &mdash; sixty-seven such blocks, and a missing one
 * fails silently, because mod resource packs are stacked in load order with no tiebreak.
 *
 * <p>
 * So the rule is asked of the item instead. {@link GearPool#owned} already decides what a barrel may
 * hand out, and it is the same question: <em>is this a thing the rifts are the source of?</em> Asked
 * here as well, a recipe cannot survive that a barrel would duplicate, and gear the rifts refuse
 * cannot lose the recipe it needs. The two answers are one answer.
 *
 * <h2>The escape hatch is {@code riftfall:gear_denied}</h2>
 * Some modded gear is a mod's own progression rather than loot &mdash; MekaSuit tiers, hazmat,
 * JustDireThings' ladder, Voidscape's ascent. Those are already named in that tag so the barrels
 * leave them alone, and the same entry now also means "and this mod keeps its recipes". Adding gear
 * to a mod's own ladder is one line in one file, in both directions at once.
 *
 * <h2>{@code riftfall:rift_only} is the other way in</h2>
 * TaCZ's guns and attachments are one item each, told apart by data, so no pool bucket fits them. The tag names
 * them and any recipe of any type that makes one goes; ammo and the benches are other items and stay.
 *
 * <p>{@code riftfall:recipe_banned} also removes recipes for explicitly forbidden outputs outside
 * the gear classifier. It affects crafting only and does not add those items to rift loot.
 *
 * <p>{@link GearPool#unaffixedAccessory} is the same, decided by slot tags: jewellery and gloves filed by
 * Accessories, Trinkets or Aether, which no rift can affix, so they are banned here and never drawn.
 *
 * <p>Iron's Spellbooks supplies class skills and loot, not a second crafting progression. Its
 * recipe namespace, serializers and outputs are removed independently of gear classification.
 * Serializer checks also cover dynamic cauldron recipes that report no fixed result.
 *
 * <h2>What this does not reach</h2>
 * A recipe is one route. Structure chests, mob drops, villager trades and fishing are others, and
 * this cannot see any of them; they are stripped elsewhere or not at all.
 */
public final class GearRecipeBan {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Recipes the last {@link #strip} removed, or -1 before it has run: the probe's proof it ran. */
    private static volatile int lastRemoved = -1;

    /** Of {@link #lastRemoved}, those removed for making a {@code riftfall:rift_only} item. */
    private static volatile int lastRiftOnly = -1;

    /** Of {@link #lastRemoved}, those removed for making an {@link #ACCESSORY}. */
    private static volatile int lastAccessory = -1;

    /** The bucket name a {@code riftfall:rift_only} result is counted under in the log. */
    static final String RIFT_ONLY = "RIFT_ONLY";

    private GearRecipeBan() {
    }

    /** What the last {@link #strip} removed, or -1 if it has not run on this server. */
    public static int lastRemoved() {
        return lastRemoved;
    }

    /** What the last {@link #strip} removed for making a {@code riftfall:rift_only} item, or -1. */
    public static int lastRiftOnly() {
        return lastRiftOnly;
    }

    /** What the last {@link #strip} removed for making an unaffixable worn accessory, or -1. */
    public static int lastAccessory() {
        return lastAccessory;
    }

    /**
     * Drops every recipe whose result the rifts own or whose output is explicitly recipe-banned.
     *
     * <p>
     * Called once per datapack load, from the one point where the recipes are loaded and the tags
     * are current at the same time. See {@code MixinReloadableServerResources} for why that is not
     * where the recipes are built.
     */
    public static void strip(RecipeManager manager, HolderLookup.Provider registries) {
        AccessorRecipeManager recipes = (AccessorRecipeManager) manager;
        Set<ResourceLocation> banned = new HashSet<>();
        Map<String, Integer> byMod = new HashMap<>();
        Map<String, Integer> bySlot = new HashMap<>();
        Set<String> locked = new TreeSet<>();
        for (Map.Entry<ResourceLocation, RecipeHolder<?>> entry : recipes.getByName().entrySet()) {
            Locked made = locked(entry.getValue(), registries);
            if (made != null) {
                banned.add(entry.getKey());
                byMod.merge(made.item().getNamespace(), 1, Integer::sum);
                bySlot.merge(made.slot(), 1, Integer::sum);
                locked.add(made.item() + "=" + made.slot());
            }
        }
        lastRemoved = banned.size();
        lastRiftOnly = bySlot.getOrDefault(RIFT_ONLY, 0);
        lastAccessory = bySlot.getOrDefault(ACCESSORY, 0);
        if (banned.isEmpty()) {
            LOGGER.info("Gear recipe ban: nothing to remove, so no modded gear in this pack is craftable.");
            return;
        }

        ImmutableMultimap.Builder<RecipeType<?>, RecipeHolder<?>> byType = ImmutableMultimap.builder();
        Multimap<RecipeType<?>, RecipeHolder<?>> loaded = recipes.getByType();
        for (Map.Entry<RecipeType<?>, RecipeHolder<?>> entry : loaded.entries()) {
            if (!banned.contains(entry.getValue().id())) {
                byType.put(entry.getKey(), entry.getValue());
            }
        }
        ImmutableMap.Builder<ResourceLocation, RecipeHolder<?>> byName = ImmutableMap.builder();
        recipes.getByName().forEach((id, holder) -> {
            if (!banned.contains(id)) {
                byName.put(id, holder);
            }
        });

        int before = loaded.size();
        recipes.setByType(byType.build());
        recipes.setByName(byName.build());
        LOGGER.info("Gear recipe ban: removed {} of {} recipes; modded gear now comes only from rifts.",
                banned.size(), before);
        LOGGER.info("Gear recipe ban: by the mod that made the item — {}", breakdown(byMod));
        // The bucket is the reason, and the reason is what a surprise on the mod list needs. A lamp
        // counted as gear did not get there by being armour; it got there by whichever test it tripped.
        LOGGER.info("Gear recipe ban: by the bucket it was classed into — {}", breakdown(bySlot));
        LOGGER.debug("Gear recipe ban: locked items — {}", String.join(", ", locked));
    }

    /** One banned result: what it makes, and the bucket the rifts would have dropped it in (or RIFT_ONLY). */
    private record Locked(ResourceLocation item, String slot) {
    }

    /**
     * Which mods paid, and how much, biggest first.
     *
     * <p>
     * A total is not a blast radius. Five thousand recipes off one material mod is the sweep working;
     * the same five thousand spread over the machine mods is a progression ladder quietly cut, and the
     * two are the same number. Grouped by the item's mod rather than the recipe's, because the
     * question this answers is whose gear stopped being makeable.
     */
    private static String breakdown(Map<String, Integer> byMod) {
        if (byMod.isEmpty()) {
            return "nothing";
        }
        return byMod.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    /**
     * The item a recipe makes if the rifts are its source, or null if this recipe stands.
     *
     * <p>
     * A recipe that cannot say what it makes is left alone. Custom recipe classes from eighty-seven
     * mods answer this in their own way and some of them throw; the alternative to catching that is
     * one badly behaved mod stopping a world from loading.
     */
    private static @Nullable Locked locked(RecipeHolder<?> recipe, HolderLookup.Provider registries) {
        // Iron's is a skill/loot donor, not a parallel crafting progression. Check identity and
        // serializer before asking for an output: cauldron/scroll recipes can have dynamic results.
        var serializer = recipe.value().getSerializer();
        if (nativeSpellProgression(recipe.id()) || (serializer != null
                && nativeSpellProgression(BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer)))) {
            return new Locked(recipe.id(), "NATIVE_SPELL_PROGRESSION");
        }
        // Apotheosis' station recipes have no fixed result; the Jeweler and Blacksmith replace them.
        if (serializer != null && apotheosisStation(BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer))) {
            return new Locked(recipe.id(), APOTHEOSIS_STATION);
        }
        ItemStack result;
        try {
            result = recipe.value().getResultItem(registries);
        } catch (Throwable unanswerable) {
            LOGGER.debug("Gear recipe ban: {} would not say what it makes, so it stands.",
                    recipe.id(), unanswerable);
            return null;
        }
        if (result.isEmpty()) {
            return null;
        }
        Holder<Item> holder = BuiltInRegistries.ITEM.wrapAsHolder(result.getItem());
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(result.getItem());
        // Also catches addon/datapack recipes under a different namespace.
        if (nativeSpellProgression(item)) {
            return new Locked(item, "NATIVE_SPELL_PROGRESSION");
        }
        if (holder.is(RiftfallTags.RECIPE_BANNED)) {
            return new Locked(item, "RECIPE_BANNED");
        }
        // TaCZ's gun smith results are only filled in by its TagsUpdatedEvent listener, which runs before this hook.
        if (holder.is(RiftfallTags.RIFT_ONLY)) {
            return new Locked(item, RIFT_ONLY);
        }
        GearPool.Slot slot = GearPool.owned(holder);
        if (slot != null) {
            return new Locked(item, slot.name());
        }
        return GearPool.unaffixedAccessory(holder) == null ? null : new Locked(item, ACCESSORY);
    }

    /** The bucket for worn jewellery and gloves outside Curios' affix slots (Aether, Accessories, Trinkets). */
    static final String ACCESSORY = "ACCESSORY";

    /** The result rule {@link #strip} applies, for other ways of making items such as resident conversions. */
    public static boolean bannedResult(ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        Holder<Item> holder = BuiltInRegistries.ITEM.wrapAsHolder(result.getItem());
        return nativeSpellProgression(BuiltInRegistries.ITEM.getKey(result.getItem()))
                || holder.is(RiftfallTags.RECIPE_BANNED) || holder.is(RiftfallTags.RIFT_ONLY)
                || GearPool.owned(holder) != null || GearPool.unaffixedAccessory(holder) != null;
    }

    /** The bucket for Apotheosis' socketing, cutting, reforging and salvage recipes. */
    static final String APOTHEOSIS_STATION = "APOTHEOSIS_STATION";

    /** Apotheosis' station serializers and tool-tier smithing (dropped 2026-11-03); potion charms stay. */
    public static final Set<String> APOTHEOSIS_STATION_SERIALIZERS = Set.of("socketing", "withdrawal", "add_sockets",
            "unnaming", "malice", "supremacy", "reforging", "salvaging", "purity_upgrade", "basic_gem_cutting", "sized_upgrade_recipe");

    static boolean apotheosisStation(@Nullable ResourceLocation serializer) {
        return serializer != null && serializer.getNamespace().equals("apotheosis")
                && APOTHEOSIS_STATION_SERIALIZERS.contains(serializer.getPath());
    }

    static boolean nativeSpellProgression(@Nullable ResourceLocation id) {
        return id != null && id.getNamespace().equals("irons_spellbooks");
    }
}
