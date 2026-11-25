package com.cyberday1.riftfall.block.marker;

import com.cyberday1.riftfall.block.marker.MarkerShapes.ObjectiveMarkerBlock;
import com.cyberday1.riftfall.block.marker.MarkerShapes.RiftChestMarkerBlock;
import com.cyberday1.riftfall.block.marker.MarkerShapes.RiftMobSpawnerMarkerBlock;
import com.cyberday1.riftfall.block.marker.MarkerShapes.SpringMarkerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registration and reverse-lookup for the nine rift marker blocks.
 *
 * <p>
 * Registration exists to close one specific hole: the structure import rewrites {@code wotr:} block
 * ids to {@code riftfall:}, and 1.21.1 resolves an unregistered id to {@code minecraft:air} with no
 * log line whatsoever. Every marker id an authored template can name therefore has to be present
 * before the rewrite runs, or the rooms grow holes nobody can trace.
 *
 * <p>
 * The lookup exists for the passes that come after placement — loot population, quest anchoring,
 * spawner activation — which all need to answer "is the block at this position one of ours, and
 * which one?" over a freshly placed room. {@link #identify(BlockState)} is that question, in O(1).
 */
public final class RiftfallMarkers {

    private static Map<MarkerKind, DeferredBlock<? extends Block>> blocks = Map.of();

    /**
     * Reverse map, block instance -> kind. Built once and read many times; identity semantics
     * because {@link Block} does not override equals/hashCode anyway.
     */
    private static volatile Map<Block, MarkerKind> lookup;

    private RiftfallMarkers() {
    }

    // -------------------------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------------------------

    /** Registers every marker. Called once, from the block-init class. */
    public static Map<MarkerKind, DeferredBlock<? extends Block>> registerAll(DeferredRegister.Blocks registry) {
        if (!blocks.isEmpty()) {
            throw new IllegalStateException("Marker blocks have already been registered");
        }
        EnumMap<MarkerKind, DeferredBlock<? extends Block>> registered = new EnumMap<>(MarkerKind.class);

        registered.put(MarkerKind.RIFT_CHEST, registry.register(MarkerKind.RIFT_CHEST.blockId(),
                () -> new RiftChestMarkerBlock(markerProperties().noOcclusion())));
        registered.put(MarkerKind.SPRING_BLOCK, registry.register(MarkerKind.SPRING_BLOCK.blockId(),
                () -> new SpringMarkerBlock(markerProperties())));
        registered.put(MarkerKind.QUEST_HUB, registry.register(MarkerKind.QUEST_HUB.blockId(),
                () -> new Block(markerProperties())));
        registered.put(MarkerKind.OBJECTIVE_BLOCK, registry.register(MarkerKind.OBJECTIVE_BLOCK.blockId(),
                () -> new ObjectiveMarkerBlock(markerProperties())));
        registered.put(MarkerKind.RIFT_MOB_SPAWNER, registry.register(MarkerKind.RIFT_MOB_SPAWNER.blockId(),
                () -> new RiftMobSpawnerMarkerBlock(markerProperties())));
        registered.put(MarkerKind.EXIT_PORTAL, registry.register(MarkerKind.EXIT_PORTAL.blockId(),
                () -> new Block(markerProperties())));
        registered.put(MarkerKind.BOSS_ARENA, registry.register(MarkerKind.BOSS_ARENA.blockId(),
                () -> new Block(markerProperties())));
        registered.put(MarkerKind.BOSS_ENTRY, registry.register(MarkerKind.BOSS_ENTRY.blockId(),
                () -> new Block(markerProperties())));
        registered.put(MarkerKind.BOSS_GATE, registry.register(MarkerKind.BOSS_GATE.blockId(),
                () -> new Block(markerProperties())));

        for (MarkerKind kind : MarkerKind.ALL) {
            if (!registered.containsKey(kind)) {
                throw new IllegalStateException("Marker " + kind + " was not registered");
            }
        }
        blocks = Collections.unmodifiableMap(registered);
        return blocks;
    }

    /**
     * Shared properties. Deliberately unremarkable: a marker is a solid, breakable cube until the
     * behaviour work gives it a reason to be anything else.
     */
    private static BlockBehaviour.Properties markerProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(2.5F)
                .sound(SoundType.STONE);
    }

    // -------------------------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------------------------

    /** Every marker, keyed by kind. */
    public static Map<MarkerKind, DeferredBlock<? extends Block>> holders() {
        return blocks;
    }

    /** The holder for one marker — safe to hold at class-init time. */
    public static DeferredBlock<? extends Block> holder(MarkerKind kind) {
        DeferredBlock<? extends Block> held = blocks.get(kind);
        if (held == null) {
            throw new IllegalStateException("Marker blocks have not been registered yet");
        }
        return held;
    }

    /** The live block for one marker. Only valid once registration has run. */
    public static Block block(MarkerKind kind) {
        return holder(kind).get();
    }

    /** O(1): is this state one of the markers, and if so which? */
    public static Optional<MarkerKind> identify(BlockState state) {
        return state == null ? Optional.empty() : identify(state.getBlock());
    }

    /** O(1): is this block one of the markers, and if so which? */
    public static Optional<MarkerKind> identify(Block block) {
        return Optional.ofNullable(lookupMap().get(block));
    }

    /** Predicate form of {@link #identify(BlockState)}. */
    public static boolean isMarker(BlockState state) {
        return state != null && lookupMap().containsKey(state.getBlock());
    }

    /**
     * Builds the reverse map. Called from common setup so the first real query is already warm;
     * safe to call more than once.
     */
    public static void buildLookup() {
        lookupMap();
    }

    private static Map<Block, MarkerKind> lookupMap() {
        Map<Block, MarkerKind> local = lookup;
        if (local == null) {
            synchronized (RiftfallMarkers.class) {
                local = lookup;
                if (local == null) {
                    local = createLookup();
                    lookup = local;
                }
            }
        }
        return local;
    }

    private static Map<Block, MarkerKind> createLookup() {
        Map<Block, MarkerKind> map = new IdentityHashMap<>(MarkerKind.ALL.length);
        for (MarkerKind kind : MarkerKind.ALL) {
            map.put(block(kind), kind);
        }
        return Collections.unmodifiableMap(map);
    }
}
