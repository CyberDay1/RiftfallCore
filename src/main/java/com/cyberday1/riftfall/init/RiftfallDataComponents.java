package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data components for Riftfall Core. */
public final class RiftfallDataComponents {

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, RiftfallCore.MODID);

    /**
     * The difficulty a rift key was forged at.
     *
     * <p>
     * Persistent, because a key is an item and has to survive being put in a chest; network
     * synchronised, because the tooltip that reads it is drawn on the client.
     *
     * <p>
     * Positive with no ceiling. What a tier <em>does</em> — how it moves the room library, the mob
     * budget, the loot — is not decided here, and the number that turns out to be the top of the
     * ladder belongs with whatever decides it rather than being guessed at now.
     *
     * <p>
     * There is deliberately no seed alongside it. The rift is rolled when a portal is lit, not when
     * a key is forged, so that a key used twice opens two different dungeons and a party lighting
     * one portal shares a single rift between them.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RIFT_TIER =
            COMPONENTS.registerComponentType("rift_tier", builder -> builder
                    .persistent(ExtraCodecs.POSITIVE_INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Re-temper steps the Blacksmith has taken on this item; the rank band (B1) counts them. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RETEMPERED =
            COMPONENTS.registerComponentType("retempered", builder -> builder
                    .persistent(ExtraCodecs.POSITIVE_INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** The Fortune level the Blacksmith (or /riftfall pick) granted; Fortune above it does nothing (FortuneGrant). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FORTUNE_GRANT =
            COMPONENTS.registerComponentType("fortune_grant", builder -> builder
                    .persistent(ExtraCodecs.NON_NEGATIVE_INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** The theme(s) a Cartographer attuned a rift key to; the rift rolls between them. The key keeps its tier. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.List<ResourceLocation>>> RIFT_THEME =
            COMPONENTS.registerComponentType("rift_theme", builder -> builder
                    .persistent(ResourceLocation.CODEC.listOf())
                    .networkSynchronized(ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(2))));

    /** The boss an Expert Cartographer attuned a rift key to. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> RIFT_BOSS =
            COMPONENTS.registerComponentType("rift_boss", builder -> builder
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC));

    /**
     * The rift a piece of gear was carried into, written on it at the door.
     *
     * <p>
     * This is what tells a party's own kit from what the dungeon handed them, and it is a mark on the
     * item because nothing else can be. The snapshot on the run says what the inventory looked like
     * going in, which is enough to count with and not enough to identify with: two identical swords
     * are the same entry in that list, and a party that walks in with one, finds a second and dies
     * has a list that says "a sword" and two swords to point it at. A player who wanted to keep the
     * better one only has to drop the worse.
     *
     * <p>
     * The value is the rift's own dimension, rather than a flag, so a mark cannot be brought back
     * from somewhere else. A run instance is named uniquely and is deleted when it ends, so a sword
     * marked with yesterday's rift matches nothing today, and the check at the point of death is a
     * comparison against the level the player is standing in and needs to look nothing up.
     *
     * <p>
     * Only ever written on items that do not stack. A mark makes two otherwise identical stacks
     * refuse to merge, which for a stackable would mean a marked half-stack of arrows sitting beside
     * an unmarked one for the rest of the run &mdash; a visible, permanent tax on carrying anything
     * ordinary. Stackables are told apart by counting against the snapshot instead, which is exactly
     * as strong for things that are interchangeable by definition.
     *
     * <p>
     * Persistent, because a player can log out inside a rift and the mark has to still be there when
     * they come back. It is not cleared on the way out: the next entry overwrites it, and outside a
     * rift it names a dimension that no longer exists, so it is inert rather than stale.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> CARRIED_INTO =
            COMPONENTS.registerComponentType("carried_into", builder -> builder
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC));

    /** A rift-dropped TaCZ gun's tier and rarity. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.cyberday1.riftfall.gear.GunGrade>>
            GUN_GRADE = COMPONENTS.registerComponentType("gun_grade", builder -> builder
                    .persistent(com.cyberday1.riftfall.gear.GunGrade.CODEC)
                    .networkSynchronized(com.cyberday1.riftfall.gear.GunGrade.STREAM_CODEC));

    /** Which story note a lost note is (LoreNotes id); its text comes from the lang files. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> NOTE_ID =
            COMPONENTS.registerComponentType("note_id", builder -> builder
                    .persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /**
     * Whose core a Shattered World Shard is: theme, defender and generated names, written at the boss kill. Two
     * worlds' shards do not stack; one world's do. Consumed with the shard at the Core.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.cyberday1.riftfall.lore.ShardOrigin>> SHARD_ORIGIN =
            COMPONENTS.registerComponentType("shard_origin", builder -> builder
                    .persistent(com.cyberday1.riftfall.lore.ShardOrigin.CODEC)
                    .networkSynchronized(com.cyberday1.riftfall.lore.ShardOrigin.STREAM_CODEC));

    private RiftfallDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
