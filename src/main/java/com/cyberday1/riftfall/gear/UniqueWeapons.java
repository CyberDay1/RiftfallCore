package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.init.RiftfallTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Unique, legendary and boss-tier weapons ({@code riftfall:unique_weapons}). They stay gear (floors, recipe ban) but
 * never come out of a barrel or a class family; a rift boss drops one now and then from {@link #MIN_TIER} up
 * (the 5% lives in {@code entities/rift_boss_arms}' pool condition).
 */
public final class UniqueWeapons {

    /** The lowest rift tier whose boss can drop one (assumed default 2026-10-11). */
    public static final int MIN_TIER = 5;

    private UniqueWeapons() {
    }

    public static boolean isUnique(Item item) {
        return BuiltInRegistries.ITEM.wrapAsHolder(item).is(RiftfallTags.UNIQUE_WEAPONS);
    }

    /** Whether a barrel or a class family may hand this weapon out. */
    public static boolean barrelDrawable(Item item) {
        return !isUnique(item);
    }

    /** Probe use: the tier the boss entry reads instead of its level's, or null for the level's own. */
    private static volatile @org.jetbrains.annotations.Nullable Integer tierOverride;

    public static int tier(int levelTier) {
        Integer override = tierOverride;
        return override != null ? override : levelTier;
    }

    /** Probe use only; no command reaches it. */
    public static void overrideTierForProbe(@org.jetbrains.annotations.Nullable Integer tier) {
        tierOverride = tier;
    }

    /** The pool's uniques (the tag cut with the gear pool's weapons, so a non-weapon in the tag never drops). */
    public static List<Item> pool() {
        return GearPool.weapons().keySet().stream()
                .filter(UniqueWeapons::isUnique)
                .sorted((a, b) -> BuiltInRegistries.ITEM.getKey(a).compareTo(BuiltInRegistries.ITEM.getKey(b)))
                .toList();
    }

    /** One unique, uniformly; empty when the pool has none. */
    public static ItemStack draw(RandomSource random) {
        List<Item> pool = pool();
        return pool.isEmpty() ? ItemStack.EMPTY : new ItemStack(pool.get(random.nextInt(pool.size())));
    }
}
