package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.barrel.BarrelTier;
import com.cyberday1.riftfall.block.marker.MarkerKind;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.block.marker.PoiAnchorKind;
import com.cyberday1.riftfall.block.processor.ProcessorBlockFamily;
import com.cyberday1.riftfall.block.processor.ProcessorVariant;
import com.cyberday1.riftfall.rift.RiftAnchorItem;
import com.cyberday1.riftfall.rift.RiftFlareItem;
import com.cyberday1.riftfall.rift.RiftKeyItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/** Item registry for Riftfall Core. */
public final class RiftfallItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RiftfallCore.MODID);

    public static final DeferredItem<com.cyberday1.riftfall.tool.LootingToolItem> LOOTING_PICK =
            ITEMS.registerItem("looting_pick", com.cyberday1.riftfall.tool.LootingToolItem::new);

    public static final DeferredItem<com.cyberday1.riftfall.tool.RunestoneItem> COMMON_RUNESTONE =
            ITEMS.registerItem("common_runestone", p -> new com.cyberday1.riftfall.tool.RunestoneItem(p, com.cyberday1.riftfall.tool.RuneGrade.COMMON));
    public static final DeferredItem<com.cyberday1.riftfall.tool.RunestoneItem> UNCOMMON_RUNESTONE =
            ITEMS.registerItem("uncommon_runestone", p -> new com.cyberday1.riftfall.tool.RunestoneItem(p, com.cyberday1.riftfall.tool.RuneGrade.UNCOMMON));
    public static final DeferredItem<com.cyberday1.riftfall.tool.RunestoneItem> RARE_RUNESTONE =
            ITEMS.registerItem("rare_runestone", p -> new com.cyberday1.riftfall.tool.RunestoneItem(p, com.cyberday1.riftfall.tool.RuneGrade.RARE));
    public static final DeferredItem<com.cyberday1.riftfall.tool.RunestoneItem> EXOTIC_RUNESTONE =
            ITEMS.registerItem("exotic_runestone", p -> new com.cyberday1.riftfall.tool.RunestoneItem(p, com.cyberday1.riftfall.tool.RuneGrade.EXOTIC));

    /**
     * A {@link BlockItem} for each of the 180 placeholders, in family-then-variant order — which
     * is also the order they appear in the creative tab.
     */
    public static final List<DeferredItem<BlockItem>> PROCESSOR_ITEMS = registerProcessorItems();

    /**
     * A {@link BlockItem} for each of the five markers, in {@link MarkerKind} order.
     *
     * <p>
     * They are inert, but a marker you cannot hold is a marker you cannot place in a test room or
     * re-author a template with, so they get items on the same terms the placeholders do.
     */
    public static final List<DeferredItem<BlockItem>> MARKER_ITEMS = registerMarkerItems();

    /**
     * A {@link BlockItem} for each of the four POI anchors, in {@link PoiAnchorKind} order.
     *
     * <p>
     * These are the one set of blocks that exist <em>only</em> to be held: an anchor is placed by a
     * room author in an editor world and never by anything else.
     */
    public static final List<DeferredItem<BlockItem>> POI_ANCHOR_ITEMS = registerPoiAnchorItems();

    public static final List<DeferredItem<BlockItem>> NPC_SPAWN_MARKER_ITEMS = registerNpcSpawnMarkerItems();

    /**
     * The one-way wall, as something a room author can carry in and set down.
     *
     * <p>
     * The block cannot be mined, so this item is the only way one is ever placed &mdash; by a builder
     * authoring a boss room, or by whoever is checking the balcony still holds.
     */
    public static final DeferredItem<BlockItem> ONE_WAY_GLASS =
            ITEMS.registerSimpleBlockItem("one_way_glass", RiftfallBlocks.ONE_WAY_GLASS);

    /** Authoring marker for a story note in a home tile, resolved at placement (LoreNotes). */
    public static final DeferredItem<BlockItem> NOTE_MARKER =
            ITEMS.registerSimpleBlockItem("note_marker", RiftfallBlocks.NOTE_MARKER);

    /** Authoring marker for a gem-vein template, resolved at placement (GemVeinDecorator). */
    public static final DeferredItem<BlockItem> GEM_SEAM =
            ITEMS.registerSimpleBlockItem("gem_seam", RiftfallBlocks.GEM_SEAM);
    public static final DeferredItem<BlockItem> RIFTSTONE =
            ITEMS.registerSimpleBlockItem("riftstone", RiftfallBlocks.RIFTSTONE);

    /** A story note found in the home. */
    public static final DeferredItem<com.cyberday1.riftfall.lore.LostNoteItem> LOST_NOTE =
            ITEMS.registerItem("lost_note", com.cyberday1.riftfall.lore.LostNoteItem::new);

    /**
     * A {@link BlockItem} for each of the four loot barrels, in tier order.
     *
     * <p>
     * Nothing in survival gives one out — a barrel is placed by worldgen and drops nothing when
     * broken — but a barrel you cannot set down is a barrel nobody can look at while the art is
     * being judged, so they are carryable on the terms the markers are.
     */
    public static final List<DeferredItem<BlockItem>> BARREL_ITEMS = registerBarrelItems();

    /** The key forge, as something a player can carry and set down. */
    public static final DeferredItem<BlockItem> KEY_FORGE =
            ITEMS.registerSimpleBlockItem("key_forge", RiftfallBlocks.KEY_FORGE);

    /**
     * The world breaker: crafted (amethyst shards in the corners, obsidian on the edges, an
     * amethyst block in the middle) and placed by the player, then lit with a key.
     *
     * <p>
     * Unlike the tear or the portal, this is a device a player deliberately places, so a
     * {@link BlockItem} is not decorative here &mdash; it is how the block reaches the world at all.
     * It is in {@code riftfall:not_gear} so the gear recipe ban can never take its recipe.
     */
    public static final DeferredItem<BlockItem> WORLD_BREAKER =
            ITEMS.registerSimpleBlockItem("world_breaker", RiftfallBlocks.WORLD_BREAKER);

    /** The world resurrection machine: placed in the shattered home dimension and fed shards. */
    public static final DeferredItem<BlockItem> WORLD_RESURRECTION_MACHINE =
            ITEMS.registerSimpleBlockItem("world_resurrection_machine", RiftfallBlocks.WORLD_RESURRECTION_MACHINE);

    /**
     * The rift shard: what a key is forged out of.
     *
     * <p>
     * Stacks normally, because the cost of a key scales with its tier and a tier is a count of these.
     *
     * <p>
     * It has no recipe and no loot table yet — for now it comes from {@code /give} and the creative
     * tab, and the tables that will drop it inside rifts come later. Its authored model is a
     * fractured amethyst blade using the World Breaker's mineral material vocabulary.
     */
    public static final DeferredItem<Item> RIFT_SHARD = ITEMS.registerSimpleItem("rift_shard");

    /** The Jeweler's purity fodder: dissolved gems, and legacy Apotheosis gem dust 1:1. No crafting recipe. */
    public static final DeferredItem<Item> GEM_GRIT = ITEMS.registerSimpleItem("gem_grit");

    /** Consumed by the Jeweler's "add a socket" (Journeyman); legacy Sigils of Socketing convert to it 1:1. */
    public static final DeferredItem<Item> SOCKET_CHISEL = ITEMS.registerSimpleItem("socket_chisel");

    /** The Jeweler's late purity material, made by the Quarry at Expert as a work order. No crafting recipe. */
    public static final DeferredItem<Item> FACETED_CRYSTAL = ITEMS.registerSimpleItem("faceted_crystal");

    /** What the Blacksmith's re-temper spends: from the Quarry's crystals or the Blacksmith's salvage. No crafting recipe. */
    public static final DeferredItem<Item> TEMPERING_STOCK = ITEMS.registerSimpleItem("tempering_stock");

    /**
     * The shattered world shard: a boss trophy and endgame currency.
     *
     * <p>
     * Stacks normally. Dropped at 50% chance per player on a rift boss kill (see
     * {@link com.cyberday1.riftfall.loot.RiftBossLoot}), so a party of four averages two per fight. A boss-dropped
     * shard carries its world ({@link RiftfallDataComponents#SHARD_ORIGIN}), so shards of two worlds keep apart.
     */
    public static final DeferredItem<com.cyberday1.riftfall.lore.ShatteredShardItem> SHATTERED_WORLD_SHARD =
            ITEMS.registerItem("shattered_world_shard", com.cyberday1.riftfall.lore.ShatteredShardItem::new);

    /**
     * The rift key, which lights a copper frame and sets the difficulty of what is behind it.
     *
     * <p>
     * Stacks to one. Two keys forged at different tiers are different things, and a stack can only
     * carry one set of components, so a stack of them would have to either silently merge tiers or
     * refuse to merge at all — both worse than not stacking.
     *
     * <p>
     * It wears art carried over from Unbound Ascension's mystery key: an 18-element Blockbench
     * model, so like {@code key_forge} it is authored by hand under {@code src/main/resources}
     * rather than generated. There is no item-model provider to point at it; the model and the
     * texture sit at the paths the item's registry name already implies.
     */
    public static final DeferredItem<RiftKeyItem> RIFT_KEY =
            ITEMS.registerItem("rift_key", properties -> new RiftKeyItem(properties.stacksTo(1)));

    /**
     * The rift anchor: a way out of a dungeon from wherever you are standing, spent on use.
     *
     * <p>
     * Stacks normally, unlike the key. Two anchors are the same anchor — there is nothing on one to
     * tell it from another — and a party that wants three of them between them should be able to
     * carry them in one slot.
     *
     * <p>
     * Its authored copper compass surrounds an amethyst focus. The recipe is a number picked so
     * the item can be had in a test world rather than one anybody has balanced.
     */
    public static final DeferredItem<RiftAnchorItem> RIFT_ANCHOR =
            ITEMS.registerItem("rift_anchor", RiftAnchorItem::new);

    /**
     * The rift flare: the taunt. Burn one and every boss in the room turns on you.
     *
     * <p>
     * Stacks, for the anchor's reason — one flare is every other flare, and a party carrying six
     * between them should not be carrying six slots of them.
     *
     * <p>
     * Its authored model is a caged amethyst flare with a wrapped grip and a pulsing crystal.
     */
    public static final DeferredItem<RiftFlareItem> RIFT_FLARE =
            ITEMS.registerItem("rift_flare", RiftFlareItem::new);

    private RiftfallItems() {
    }

    private static List<DeferredItem<BlockItem>> registerMarkerItems() {
        List<DeferredItem<BlockItem>> items = new ArrayList<>(MarkerKind.ALL.length);
        for (MarkerKind kind : MarkerKind.ALL) {
            // Through RiftfallBlocks.MARKERS rather than RiftfallMarkers directly: touching the
            // init class is what forces block registration to have happened first, whatever order
            // the fields in this class end up in.
            items.add(ITEMS.registerSimpleBlockItem(kind.blockId(), RiftfallBlocks.MARKERS.get(kind)));
        }
        return List.copyOf(items);
    }

    private static List<DeferredItem<BlockItem>> registerPoiAnchorItems() {
        List<DeferredItem<BlockItem>> items = new ArrayList<>(PoiAnchorKind.ALL.length);
        for (PoiAnchorKind kind : PoiAnchorKind.ALL) {
            items.add(ITEMS.registerSimpleBlockItem(kind.blockId(), RiftfallBlocks.POI_ANCHORS.get(kind)));
        }
        return List.copyOf(items);
    }

    private static List<DeferredItem<BlockItem>> registerBarrelItems() {
        List<DeferredItem<BlockItem>> items = new ArrayList<>(BarrelTier.ALL.length);
        for (BarrelTier tier : BarrelTier.ALL) {
            items.add(ITEMS.registerSimpleBlockItem(tier.blockId(), RiftfallBlocks.BARRELS.get(tier)));
        }
        return List.copyOf(items);
    }

    private static List<DeferredItem<BlockItem>> registerNpcSpawnMarkerItems() {
        List<DeferredItem<BlockItem>> items = new ArrayList<>();
        for (NpcRole role : NpcRole.values()) {
            items.add(ITEMS.registerSimpleBlockItem(role.blockId(), RiftfallBlocks.NPC_SPAWN_MARKERS.get(role)));
        }
        return List.copyOf(items);
    }

    private static List<DeferredItem<BlockItem>> registerProcessorItems() {
        List<DeferredItem<BlockItem>> items = new ArrayList<>();
        for (ProcessorBlockFamily family : RiftfallBlocks.PROCESSOR_FAMILIES) {
            for (ProcessorVariant variant : ProcessorVariant.values()) {
                items.add(ITEMS.registerSimpleBlockItem(family.slot().blockId(variant), family.holder(variant)));
            }
        }
        return List.copyOf(items);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
