package com.cyberday1.riftfall.worldgen.decorator;

import com.cyberday1.riftfall.init.RiftfallRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Function;

/**
 * A post-substitution, context-sensitive pass over a placed room: cobwebs in corners, vines under
 * ceilings, mushrooms on damp floors.
 *
 * <p>
 * Decorators are configured in the <em>theme</em> file, not in a processor list, because they are
 * part of what a theme looks like — a theme author writes one JSON and gets both the materials and
 * the dressing. New types are added in Java by any mod through the
 * {@link RiftfallRegistries#DECORATOR_TYPES decorator type registry}.
 *
 * <h2>The neighbourhood contract</h2>
 * A decorator is handed a 7-slot array and returns a <strong>bitmask of the slots it changed</strong>.
 * Slots 0-5 are the six neighbours indexed by {@link Direction#ordinal()} (DOWN, UP, NORTH, SOUTH,
 * WEST, EAST); slot 6 is the centre. The bitmask exists so the driver writes back only what moved
 * <em>and</em> so a decorator may legally rewrite its neighbours rather than only itself — vines,
 * cobwebs and dripstone all hang off a solid block into the air beside it, so a centre-only
 * contract cannot express them.
 *
 * <p>
 * A neighbour slot is {@code null} when that position is outside the room's block list. Treat null
 * as "unknown, do not touch"; the driver discards writes to null slots.
 */
public interface RiftDecorator {

    /** Neighbourhood array index of the block being visited. */
    int CENTRE = 6;

    /** Size of the neighbourhood array handed to {@link #decorate}. */
    int NEIGHBOURHOOD_SIZE = 7;

    /** Bit for {@link #CENTRE} in the returned mask. */
    int CENTRE_BIT = 1 << CENTRE;

    /**
     * Dispatches on {@code "type"} through the decorator type registry, so
     * {@code {"type": "riftfall:vine", "rarity": 0.2}} in a theme file resolves to
     * {@link VineDecorator}.
     */
    Codec<RiftDecorator> CODEC = Codec.lazyInitialized(
            () -> RiftfallRegistries.DECORATOR_TYPES.byNameCodec()
                    .dispatch("type", RiftDecorator::codec, Function.identity()));

    /** The codec this decorator was deserialised by; the dispatch key. */
    MapCodec<? extends RiftDecorator> codec();

    /**
     * Inspects and optionally rewrites the neighbourhood.
     *
     * @param context      per-position seed source and position, already deterministic
     * @param neighbourhood 7 slots as described above; mutate in place
     * @return bitmask of mutated slots, {@code 0} for "changed nothing"
     */
    int decorate(DecoratorContext context, BlockState[] neighbourhood);
}
