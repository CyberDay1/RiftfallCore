package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.loot.GearEntry;
import com.cyberday1.riftfall.loot.RiftArmsEntry;
import com.cyberday1.riftfall.loot.RiftShardsEntry;
import com.cyberday1.riftfall.loot.ThemeCrystalEntry;
import com.cyberday1.riftfall.loot.UniqueWeaponEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Loot pool entry types for Riftfall Core.
 *
 * <p>
 * Both of these exist so that a draw a datapack cannot spell out by hand still happens
 * <em>inside</em> {@code LootTable#getRandomItems}, where Apotheosis' affix conversion, NeoForge's
 * loot modifiers and {@code MixinLootTable}'s rarity re-roll all attach.
 */
public final class RiftfallLootEntries {

    public static final DeferredRegister<LootPoolEntryType> LOOT_ENTRIES =
            DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE, RiftfallCore.MODID);

    /** {@code riftfall:gear} — one piece of modded gear, balanced across the eleven slots. */
    public static final DeferredHolder<LootPoolEntryType, LootPoolEntryType> GEAR =
            LOOT_ENTRIES.register("gear", () -> new LootPoolEntryType(GearEntry.CODEC));

    /** {@code riftfall:theme_crystal} — the crystal belonging to the rift the draw happened in. */
    public static final DeferredHolder<LootPoolEntryType, LootPoolEntryType> THEME_CRYSTAL =
            LOOT_ENTRIES.register("theme_crystal", () -> new LootPoolEntryType(ThemeCrystalEntry.CODEC));

    /** {@code riftfall:rift_arms} — a TaCZ gun or attachment for the rift's tier. */
    public static final DeferredHolder<LootPoolEntryType, LootPoolEntryType> RIFT_ARMS =
            LOOT_ENTRIES.register("rift_arms", () -> new LootPoolEntryType(RiftArmsEntry.CODEC));

    /** {@code riftfall:rift_shards} — rift shards for a barrel grade or the boss, by the rift's tier. */
    public static final DeferredHolder<LootPoolEntryType, LootPoolEntryType> RIFT_SHARDS =
            LOOT_ENTRIES.register("rift_shards", () -> new LootPoolEntryType(RiftShardsEntry.CODEC));

    public static final DeferredHolder<LootPoolEntryType, LootPoolEntryType> UNIQUE_WEAPON =
            LOOT_ENTRIES.register("unique_weapon", () -> new LootPoolEntryType(UniqueWeaponEntry.CODEC));

    public static final DeferredHolder<LootPoolEntryType, LootPoolEntryType> GEM =
            LOOT_ENTRIES.register("gem", () -> new LootPoolEntryType(com.cyberday1.riftfall.loot.RiftGemEntry.CODEC));

    public static final DeferredRegister<LootItemConditionType> LOOT_CONDITIONS =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, RiftfallCore.MODID);

    /** {@code riftfall:barrel_gear_chance}: {@code barrelGearScale} as a pay-out chance. */
    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> BARREL_GEAR_CHANCE =
            LOOT_CONDITIONS.register("barrel_gear_chance",
                    () -> new LootItemConditionType(com.cyberday1.riftfall.loot.BarrelGearChance.CODEC));

    /** {@code riftfall:in_rift}: limits Apotheosis' affix and gem modifiers to rift loot. */
    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> IN_RIFT =
            LOOT_CONDITIONS.register("in_rift", () -> new LootItemConditionType(com.cyberday1.riftfall.loot.InRift.CODEC));

    /** {@code riftfall:rift_tier_chance}: a chance that scales with the rift's tier. */
    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> RIFT_TIER_CHANCE =
            LOOT_CONDITIONS.register("rift_tier_chance",
                    () -> new LootItemConditionType(com.cyberday1.riftfall.loot.RiftTierChance.CODEC));

    private RiftfallLootEntries() {
    }

    public static void register(IEventBus modEventBus) {
        LOOT_ENTRIES.register(modEventBus);
        LOOT_CONDITIONS.register(modEventBus);
    }
}
