package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.barrel.BarrelTier;
import com.cyberday1.riftfall.block.barrel.RiftBarrelBlock;
import com.cyberday1.riftfall.block.barrel.RiftBarrels;
import com.cyberday1.riftfall.block.forge.KeyForgeBlock;
import com.cyberday1.riftfall.block.holycore.HolyCoreBlock;
import com.cyberday1.riftfall.block.breaker.WorldBreakerBlock;
import com.cyberday1.riftfall.block.glass.OneWayGlassBlock;
import com.cyberday1.riftfall.block.marker.MarkerKind;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.block.marker.NpcSpawnMarkerBlock;
import com.cyberday1.riftfall.block.marker.NpcSpawnMarkers;
import com.cyberday1.riftfall.block.marker.PoiAnchorBlock;
import com.cyberday1.riftfall.block.marker.PoiAnchorKind;
import com.cyberday1.riftfall.block.marker.PoiAnchors;
import com.cyberday1.riftfall.block.resurrection.WorldResurrectionMachineBlock;
import com.cyberday1.riftfall.block.resurrection.WorldResurrectionMachinePartBlock;
import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import com.cyberday1.riftfall.block.processor.ProcessorBlockFamily;
import com.cyberday1.riftfall.block.processor.ProcessorBlocks;
import com.cyberday1.riftfall.rift.RiftPortalBlock;
import com.cyberday1.riftfall.rift.RiftTearBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

/** Block registry for Riftfall Core. */
public final class RiftfallBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RiftfallCore.MODID);

    /** What a tier-1 portal gives off. The rungs above it add one each, topping out at 14. */
    private static final int BASE_PORTAL_LIGHT = 11;

    /**
     * The 15 processor placeholder families (180 blocks), registered in a loop rather than
     * declared one by one. Ask {@link ProcessorBlocks} for a specific family or for the reverse
     * block -> slot/variant lookup.
     */
    public static final List<ProcessorBlockFamily> PROCESSOR_FAMILIES = ProcessorBlocks.registerAll(BLOCKS);

    /**
     * The eight rift marker blocks — chest, spring, quest hub, objective, mob spawner, exit portal,
     * boss arena, boss entry. Inert for now; they are registered so that the imported structure templates' block ids
     * resolve to something instead of silently becoming air. Ask {@link RiftfallMarkers} for the
     * reverse block -> kind lookup.
     */
    public static final Map<MarkerKind, DeferredBlock<? extends Block>> MARKERS =
            RiftfallMarkers.registerAll(BLOCKS);

    /**
     * The four POI anchor blocks: a prop anchor a builder places instead of typing a pool path into
     * a jigsaw. Read at parse time and taken back out at placement, so none survives into a room.
     */
    public static final Map<PoiAnchorKind, DeferredBlock<PoiAnchorBlock>> POI_ANCHORS =
            PoiAnchors.registerAll(BLOCKS);

    /** Tile-local authoring markers for future home residents. */
    public static final Map<NpcRole, DeferredBlock<NpcSpawnMarkerBlock>> NPC_SPAWN_MARKERS =
            NpcSpawnMarkers.registerAll(BLOCKS);

    /** Where a story note goes in a home tile; replaced by its carrier when the tile is placed. */
    public static final DeferredBlock<com.cyberday1.riftfall.lore.NoteMarkerBlock> NOTE_MARKER = BLOCKS.register("note_marker",
            () -> new com.cyberday1.riftfall.lore.NoteMarkerBlock(BlockBehaviour.Properties.of().strength(-1.0F, 3600000.0F)
                    .sound(SoundType.STONE).noLootTable().noOcclusion().pushReaction(PushReaction.BLOCK)));

    /** Gem-vein authoring marker; the theme's gem_vein decorator resolves it. */
    public static final DeferredBlock<com.cyberday1.riftfall.block.ore.GemSeamBlock> GEM_SEAM = BLOCKS.register("gem_seam",
            () -> new com.cyberday1.riftfall.block.ore.GemSeamBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                    .strength(-1.0F, 3600000.0F).sound(SoundType.AMETHYST).noLootTable().pushReaction(PushReaction.BLOCK)));

    /** Glacial's crystal ore: Crystalcraft ships ice opal with no ore. Properties copy its ores. */
    public static final DeferredBlock<Block> ICE_OPAL_ORE = BLOCKS.register("ice_opal_ore",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).instrument(NoteBlockInstrument.BASEDRUM)
                    .sound(SoundType.STONE).strength(1.0F, 10.0F).requiresCorrectToolForDrops()));

    /**
     * Crystal ores on a stone or deepslate host that Crystalcraft lacks: it hosts fire opal and sunstone in
     * netherrack, maradonyx and moonstone in end stone, and has no deepslate onyx or ice opal. Ids and drops
     * are in {@code RiftOres.CRYSTAL_HOSTS}.
     */
    public static final java.util.Map<String, DeferredBlock<Block>> CRYSTAL_HOST_ORES = crystalHostOres();

    private static java.util.Map<String, DeferredBlock<Block>> crystalHostOres() {
        java.util.Map<String, DeferredBlock<Block>> out = new java.util.LinkedHashMap<>();
        for (String id : java.util.List.of("fire_opal_ore", "sunstone_ore", "maradonyx_ore", "moonstone_ore")) {
            out.put(id, BLOCKS.register(id, () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(1.0F, 10.0F)
                    .requiresCorrectToolForDrops())));
        }
        for (String crystal : java.util.List.of("onyx", "fire_opal", "sunstone", "maradonyx", "moonstone", "ice_opal")) {
            String id = "deepslate_" + crystal + "_ore";
            out.put(id, BLOCKS.register(id, () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
                    .instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.DEEPSLATE).strength(1.5F, 10.0F)
                    .requiresCorrectToolForDrops())));
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    /** Ore-room filler: mines like deepslate, drops nothing but a rare rune. */
    public static final DeferredBlock<com.cyberday1.riftfall.block.ore.RiftstoneBlock> RIFTSTONE = BLOCKS.register("riftstone",
            () -> new com.cyberday1.riftfall.block.ore.RiftstoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
                    .instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.DEEPSLATE).strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()));

    /**
     * The four loot barrels a {@code rift_chest} marker can become. Ask {@link RiftBarrels} for one
     * by tier; the map is here so that touching this class is what forces them to be registered.
     */
    public static final Map<BarrelTier, DeferredBlock<RiftBarrelBlock>> BARRELS =
            RiftBarrels.registerAll(BLOCKS);

    /**
     * The bench a rift key is forged at.
     *
     * <p>
     * It was the sixth marker until it had something to do, under the name {@code ability_bench} —
     * a Wanderers of the Rift block id that the structure import would have rewritten to ours. No
     * imported template ever named it, so it kept a name that described somebody else's block and
     * nothing of ours. As a real, craftable station it is {@code key_forge}.
     *
     * <p>
     * Sturdier than a marker and tool-agnostic: it is furniture a player places and comes back to,
     * so it breaks like a crafting table rather than like the placeholder it used to be.
     */
    public static final DeferredBlock<KeyForgeBlock> KEY_FORGE = BLOCKS.register("key_forge",
            () -> new KeyForgeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(2.5F)
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    /**
     * The device that breaks a way in: a crystal-crowned dais a key opens a tear over.
     *
     * <p>
     * The light follows {@link WorldBreakerBlock#PHASE} rather than burning always. A breaker with
     * nothing happening is a cold fitting and should read as one.
     */
    public static final DeferredBlock<WorldBreakerBlock> WORLD_BREAKER =
            BLOCKS.register("world_breaker",
                    () -> new WorldBreakerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .instrument(NoteBlockInstrument.BASEDRUM)
                            .strength(2.5F)
                            .sound(SoundType.STONE)
                            .lightLevel(state -> state.getValue(WorldBreakerBlock.PHASE).light())
                            .noOcclusion()));

    /**
     * The world core at the center of a shattered home island. Its visual phase tracks how many holy
     * relics have been offered: every ten advances one stage, from Shattered through Restored.
     */
    public static final DeferredBlock<HolyCoreBlock> HOLY_CORE = BLOCKS.register("holy_core",
            () -> new HolyCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(3.5F)
                    .sound(SoundType.STONE)
                    .lightLevel(state -> state.getValue(HolyCoreBlock.FORMED) ? 4 + state.getValue(HolyCoreBlock.PHASE) * 2 : 2)
                    .noOcclusion()));

    /**
     * The world resurrection machine: accepts shattered world shards and pushes the home dimension's
     * border outward, five blocks per side per shard. Only functional in the shattered home dimension.
     */
    /**
 * Decorative outer blocks of the 3×3 World Resurrection Machine pedestal.
 * Purely visual framing around the center controller; no logic of their own.
 */
public static final DeferredBlock<WorldResurrectionMachinePartBlock> WORLD_RESURRECTION_MACHINE_PART = BLOCKS.register("world_resurrection_machine_part",
        () -> new WorldResurrectionMachinePartBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(3.5F)
                .sound(SoundType.STONE)
                .noOcclusion()));

/**
 * The world resurrection machine: accepts shattered world shards and pushes the home dimension's
 * border outward, five blocks per side per shard. Only functional in the shattered home dimension.
 */
public static final DeferredBlock<WorldResurrectionMachineBlock> WORLD_RESURRECTION_MACHINE =
            BLOCKS.register("world_resurrection_machine",
                    () -> new WorldResurrectionMachineBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLUE)
                            .instrument(NoteBlockInstrument.BASEDRUM)
                            .strength(3.5F)
                            .sound(SoundType.STONE)
                            .requiresCorrectToolForDrops()));

    /**
     * The one-way wall on a boss balcony: a player crosses it one way and never back, and nothing
     * else crosses it at all.
     *
     * <p>
     * Unbreakable and piston-proof on the rift portal's terms. A fixture whose entire job is to be
     * the thing a player cannot get past is not one a player should be able to mine or shove aside
     * mid-fight, and a wall that can be defeated with a pickaxe is not an answer to the cheese it
     * was built for.
     *
     * <p>
     * It carries glass's two transparency predicates for the reason glass carries them: a player
     * standing inside it mid-crossing must not suffocate, and must not have the block drawn across
     * their screen.
     */
    public static final DeferredBlock<OneWayGlassBlock> ONE_WAY_GLASS = BLOCKS.register("one_way_glass",
            () -> new OneWayGlassBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(-1.0F)
                    .noLootTable()
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
                    .pushReaction(PushReaction.BLOCK)));

    /**
     * The lit surface inside a copper frame.
     *
     * <p>
     * Registered with {@code register} rather than {@code registerBlock} so that no {@code BlockItem}
     * comes with it. A portal is placed by lighting a frame and by nothing else; an item that put one
     * down anywhere would make the frame decorative.
     *
     * <p>
     * The properties are the nether portal's: no collision so you can walk into it, negative strength
     * so it cannot be mined, glass so it sounds like the surface it looks like, light 11, and
     * {@link PushReaction#BLOCK} because a piston shoving a portal sideways is a shape the detector
     * would then have to explain.
     *
     * <p>
     * The light is the one part that is not fixed: it climbs a level per rung of
     * {@link RiftPortalBlock#INTENSITY}, which is the only thing making a harder rift brighter rather
     * than merely busier.
     */
    public static final DeferredBlock<RiftPortalBlock> RIFT_PORTAL = BLOCKS.register("rift_portal",
            () -> new RiftPortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .noCollission()
                    .noLootTable()
                    .strength(-1.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> BASE_PORTAL_LIGHT + state.getValue(RiftPortalBlock.INTENSITY))
                    .pushReaction(PushReaction.BLOCK)));

    /**
     * A rift's own way out, with no frame around it.
     *
     * <p>
     * The same properties as {@link #RIFT_PORTAL}, and for the same reasons, right down to the light
     * climbing with the rung. No {@code BlockItem} either, and here the argument is stronger than it
     * is for the pane: a tear is not something anybody builds, so there is nothing for an item to be
     * the ingredient of. {@code /riftfall rift tear} is how one is put down by hand.
     */
    public static final DeferredBlock<RiftTearBlock> RIFT_TEAR = BLOCKS.register("rift_tear",
            () -> new RiftTearBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .noCollission()
                    .noLootTable()
                    .strength(-1.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> BASE_PORTAL_LIGHT + state.getValue(RiftPortalBlock.INTENSITY))
                    .pushReaction(PushReaction.BLOCK)));

    private RiftfallBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
