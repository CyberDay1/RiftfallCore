package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** Tags Riftfall Core defines. */
public final class RiftfallTags {
    /** Items disabled across recipes, loot, trades, transfers and player possession. */
    public static final TagKey<Item> ACQUISITION_BANNED =
            TagKey.create(Registries.ITEM, RiftfallCore.id("acquisition_banned"));

    /** Outputs forbidden from recipes, including utility items outside the gear classifier. */
    public static final TagKey<Item> RECIPE_BANNED =
            TagKey.create(Registries.ITEM, RiftfallCore.id("recipe_banned"));

    /** The twelve Crystalcraft theme gems the rift theme chests drop; a Jeweler cost. */
    public static final TagKey<Item> THEME_CRYSTALS =
            TagKey.create(Registries.ITEM, RiftfallCore.id("theme_crystals"));

    /** The rare theme table's crystals (onyx, fire opal, maradonyx); also theme crystals. */
    public static final TagKey<Item> RARE_CRYSTALS =
            TagKey.create(Registries.ITEM, RiftfallCore.id("rare_crystals"));

    /** Low-damage magic implements which the ordinary melee threshold cannot recognize. */
    public static final TagKey<Item> MELEE_WEAPONS =
            TagKey.create(Registries.ITEM, RiftfallCore.id("melee_weapons"));

    /**
     * What a rift portal can be framed out of: copper, in every form that is a full cube.
     *
     * <p>
     * A tag rather than a block list, so a pack can widen or narrow the frame without touching code —
     * and so the contents are a file an author can read rather than a decision buried in a static
     * initialiser.
     */
    public static final TagKey<Block> PORTAL_FRAME =
            TagKey.create(Registries.BLOCK, RiftfallCore.id("portal_frame"));

    /**
     * Gear a barrel may never hand out, because Riftfall's stats would not stick to it.
     *
     * <p>
     * Not a balance list. Every member is an item whose own mod re-applies attributes after ours —
     * a global {@code ItemAttributeModifierEvent} listener, or a hardcoded
     * {@code Item.BASE_ATTACK_DAMAGE_ID} — so the piece would arrive wearing someone else's numbers.
     */
    public static final TagKey<Item> GEAR_DENIED =
            TagKey.create(Registries.ITEM, RiftfallCore.id("gear_denied"));

    /**
     * Items the classifier can only read as gear, and which are not gear.
     *
     * <p>
     * {@code GearPool.classify} decides by what an item <em>is</em>: worn in a slot, or filed under a
     * trinket slot. Both answers are true of things nobody would call gear. Simply Light's lamps are
     * wearable on the head, and the pack's backpacks, wireless terminals, magnets, portable
     * workbenches and batteries all live in a Curios slot.
     *
     * <p>
     * Distinct from {@link #GEAR_DENIED}, which the two would otherwise be mistaken for. That tag
     * says <em>this is gear, but it is its mod's own progression</em>; this one says <em>this was
     * never gear</em>. They do the same thing and mean different things, and a tag that means two
     * things is one nobody can safely edit later.
     */
    public static final TagKey<Item> NOT_GEAR =
            TagKey.create(Registries.ITEM, RiftfallCore.id("not_gear"));

    /**
     * Items that come only out of rifts and never from the balanced gear draw: TaCZ's guns and attachments. Their
     * recipes are removed and any loot rolled outside a rift loses them; the rift draws them from {@code rift_arms}.
     */
    public static final TagKey<Item> RIFT_ONLY =
            TagKey.create(Registries.ITEM, RiftfallCore.id("rift_only"));

    /**
     * Unique, legendary and boss-tier weapons: kept out of the barrels' gear draw and the class families, and dropped
     * only by rift bosses at mid-high tiers. Edit the tag to move a weapon in or out.
     */
    public static final TagKey<Item> UNIQUE_WEAPONS =
            TagKey.create(Registries.ITEM, RiftfallCore.id("unique_weapons"));

    /** Rift-owned, never drawn by a barrel: items whose mod must generate their data (Iron's jewelry, ruled 2026-11-24). */
    public static final TagKey<Item> NOT_BARREL_DRAWN =
            TagKey.create(Registries.ITEM, RiftfallCore.id("not_barrel_drawn"));

    /** Curios that keep Riftfall's affixes and do nothing of their own: the three Relics mods' (ruled 2026-11-19). */
    public static final TagKey<Item> CURIO_SILENCED =
            TagKey.create(Registries.ITEM, RiftfallCore.id("curio_silenced"));

    /** Held items that do nothing inside a rift: the four held Relics (ruled 2026-11-20). */
    public static final TagKey<Item> RIFT_SILENCED =
            TagKey.create(Registries.ITEM, RiftfallCore.id("rift_silenced"));

    /**
     * The non-vanilla structure sets still allowed to generate.
     *
     * <p>
     * Everything outside it is stripped, so the tag holds only what the pack asked for by name. Its
     * one member today is the Nether fortress: YungsBetterNetherFortresses turns the vanilla one off
     * and stands in its place, and a structure standing in for a vanilla one is the vanilla one.
     */
    public static final TagKey<StructureSet> STRUCTURES_ALLOWED =
            TagKey.create(Registries.STRUCTURE_SET, RiftfallCore.id("structures_allowed"));

    /** Modded ore blocks no feature may place in a vanilla dimension; `_ore` names count without being listed. */
    public static final TagKey<Block> MODDED_ORES =
            TagKey.create(Registries.BLOCK, RiftfallCore.id("modded_ores"));

    /**
     * Entities a shut boss door discards, and refuses to let in while it is shut.
     *
     * <p>
     * A portal entity does not walk through a doorway: it teleports on a bounding-box overlap with no
     * geometric test and fires no cancellable event, so the arena's one aperture does not contain it.
     * The two members are the two portal entities measured in this pack; a tag rather than a list
     * because they are the two that were found, not the two that exist.
     */
    public static final TagKey<EntityType<?>> BANISHED_FROM_A_SEALED_ARENA =
            TagKey.create(Registries.ENTITY_TYPE, RiftfallCore.id("banished_from_a_sealed_arena"));

    private RiftfallTags() {
    }
}
