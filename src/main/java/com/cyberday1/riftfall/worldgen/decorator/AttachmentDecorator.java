package com.cyberday1.riftfall.worldgen.decorator;

import com.cyberday1.riftfall.theme.ThemeCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hangs a fixed blockstate in air that has enough solid faces around it: cobwebs wedged into
 * corners, lanterns under ceilings, chains off beams.
 *
 * <pre>
 * { "type": "riftfall:attachment", "rarity": 0.05, "blockstate": "minecraft:cobweb",
 *   "requires_sides": 1, "requires_up": true }
 * </pre>
 *
 * <p>
 * This decorator only ever writes the centre slot: it visits an <em>air</em> position and asks
 * whether its neighbours can hold something. Writing the neighbour instead would mean visiting the
 * solid block and choosing among up to six candidate air slots, which produces a different (and
 * much denser) distribution for the same rarity.
 */
public record AttachmentDecorator(BlockState blockState, int requiresSides, boolean requiresUp,
        boolean requiresDown, float rarity, long seed) implements RiftDecorator {

    public static final MapCodec<AttachmentDecorator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ThemeCodecs.BLOCK_STATE.fieldOf("blockstate").forGetter(AttachmentDecorator::blockState),
            Codec.intRange(0, 4).optionalFieldOf("requires_sides", 0).forGetter(AttachmentDecorator::requiresSides),
            Codec.BOOL.optionalFieldOf("requires_up", false).forGetter(AttachmentDecorator::requiresUp),
            Codec.BOOL.optionalFieldOf("requires_down", false).forGetter(AttachmentDecorator::requiresDown),
            Codec.floatRange(0.0F, 1.0F).fieldOf("rarity").forGetter(AttachmentDecorator::rarity),
            Codec.LONG.optionalFieldOf("seed", 0x5C0FFEEL).forGetter(AttachmentDecorator::seed)
    ).apply(instance, AttachmentDecorator::new));

    @Override
    public MapCodec<? extends RiftDecorator> codec() {
        return CODEC;
    }

    /**
     * The salt keeps two attachment decorators in the same theme (cobwebs and lanterns, say) from
     * rolling identically and therefore always landing on the same blocks.
     */
    public long salt() {
        // The block's name, not its identity hash: that one changes every time the game starts.
        return seed ^ BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).hashCode();
    }

    @Override
    public int decorate(DecoratorContext context, BlockState[] neighbourhood) {
        BlockState centre = neighbourhood[CENTRE];
        if (centre == null || !centre.isAir()) {
            return 0;
        }
        // Roll before the geometry checks so rarity means "of eligible air positions" consistently
        // regardless of how many faces we end up inspecting.
        if (context.roll() >= rarity) {
            return 0;
        }

        if (requiresUp && !DecoratorContext.isFaceSturdy(neighbourhood[Direction.UP.ordinal()], Direction.DOWN)) {
            return 0;
        }
        if (requiresDown && !DecoratorContext.isFaceSturdy(neighbourhood[Direction.DOWN.ordinal()], Direction.UP)) {
            return 0;
        }

        if (requiresSides > 0) {
            int found = 0;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (DecoratorContext.isFaceSturdy(neighbourhood[side.ordinal()], side.getOpposite())) {
                    found++;
                }
            }
            if (found < requiresSides) {
                return 0;
            }
        }

        neighbourhood[CENTRE] = blockState;
        return CENTRE_BIT;
    }
}
