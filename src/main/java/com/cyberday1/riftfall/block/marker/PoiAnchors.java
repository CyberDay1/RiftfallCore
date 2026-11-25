package com.cyberday1.riftfall.block.marker;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Registration and resolution for the four POI anchor blocks.
 *
 * <p>
 * Unlike {@link RiftfallMarkers} there is no reverse lookup map here: an anchor knows its own
 * category, so {@code block instanceof PoiAnchorBlock} answers both "is this one of ours" and
 * "which one" without a map to keep warm.
 */
public final class PoiAnchors {

    private static Map<PoiAnchorKind, DeferredBlock<PoiAnchorBlock>> blocks = Map.of();

    /** {@link PoiAnchorKind#finalState()} resolved to a state, built on first use. */
    private static volatile Map<PoiAnchorKind, BlockState> finalStates;

    private PoiAnchors() {
    }

    /** Registers all four. Called once, from the block-init class. */
    public static Map<PoiAnchorKind, DeferredBlock<PoiAnchorBlock>> registerAll(DeferredRegister.Blocks registry) {
        if (!blocks.isEmpty()) {
            throw new IllegalStateException("POI anchor blocks have already been registered");
        }
        EnumMap<PoiAnchorKind, DeferredBlock<PoiAnchorBlock>> registered = new EnumMap<>(PoiAnchorKind.class);
        for (PoiAnchorKind kind : PoiAnchorKind.ALL) {
            registered.put(kind, registry.register(kind.blockId(), () -> kind.hasPlacementFacing()
                    ? new PoiAnchorBlock.Facing(kind, anchorProperties())
                    : new PoiAnchorBlock(kind, anchorProperties())));
        }
        blocks = Collections.unmodifiableMap(registered);
        return blocks;
    }

    /**
     * An authoring block, so: breakable by hand in a hurry, and loud enough to tell apart from the
     * placeholder it is standing next to.
     */
    private static BlockBehaviour.Properties anchorProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(1.0F)
                .sound(SoundType.STONE);
    }

    /** Every anchor, keyed by kind. */
    public static Map<PoiAnchorKind, DeferredBlock<PoiAnchorBlock>> holders() {
        return blocks;
    }

    /** The holder for one anchor — safe to hold at class-init time. */
    public static DeferredBlock<PoiAnchorBlock> holder(PoiAnchorKind kind) {
        DeferredBlock<PoiAnchorBlock> held = blocks.get(kind);
        if (held == null) {
            throw new IllegalStateException("POI anchor blocks have not been registered yet");
        }
        return held;
    }

    /** The live block for one anchor. Only valid once registration has run. */
    public static PoiAnchorBlock block(PoiAnchorKind kind) {
        return holder(kind).get();
    }

    /** What the anchor's own cell becomes once its prop is stamped. */
    public static BlockState finalState(PoiAnchorKind kind) {
        Map<PoiAnchorKind, BlockState> local = finalStates;
        if (local == null) {
            synchronized (PoiAnchors.class) {
                local = finalStates;
                if (local == null) {
                    local = resolveFinalStates();
                    finalStates = local;
                }
            }
        }
        return local.get(kind);
    }

    private static Map<PoiAnchorKind, BlockState> resolveFinalStates() {
        EnumMap<PoiAnchorKind, BlockState> resolved = new EnumMap<>(PoiAnchorKind.class);
        for (PoiAnchorKind kind : PoiAnchorKind.ALL) {
            ResourceLocation id = kind.finalState();
            // BuiltInRegistries.BLOCK is defaulted, so a missing id would come back as air and put
            // a hole where the anchor was instead of the floor under it.
            if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                throw new IllegalStateException(kind + " names " + id + ", which is not a block");
            }
            resolved.put(kind, BuiltInRegistries.BLOCK.get(id).defaultBlockState());
        }
        return Collections.unmodifiableMap(resolved);
    }
}
