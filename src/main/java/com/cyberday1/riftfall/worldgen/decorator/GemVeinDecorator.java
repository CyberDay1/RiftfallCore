package com.cyberday1.riftfall.worldgen.decorator;

import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import com.cyberday1.riftfall.block.ore.GemSeamBlock;
import com.cyberday1.riftfall.theme.ThemeCodecs;
import com.cyberday1.riftfall.worldgen.ore.RiftOres;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * Resolves the {@code riftfall:gem_seam} markers of a gem-vein prop: gem seams become a gem ore (or the
 * stone beside them), cluster seams an amethyst cluster (or air), light seams the theme's light.
 *
 * <pre>
 * { "type": "riftfall:gem_vein", "home": "alexandrite", "host": "stone", "light": "minecraft:glowstone" }
 * </pre>
 *
 * <p>
 * {@code anchor_chance} is read by the generator, not here: the share of in-wall prop anchors (sizes
 * 5, 7, 9) that draw from {@code rift/poi/gem_vein} instead. Per position, so reach 0.
 */
public record GemVeinDecorator(Optional<String> home, RiftOres.Host host, BlockState light, BlockState fallback,
        float anchorChance, long seed) implements RiftDecorator {

    public static final float DEFAULT_ANCHOR_CHANCE = 0.05F;

    public static final MapCodec<GemVeinDecorator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("home").forGetter(GemVeinDecorator::home),
            RiftOres.Host.CODEC.optionalFieldOf("host", RiftOres.Host.STONE).forGetter(GemVeinDecorator::host),
            ThemeCodecs.BLOCK_STATE.optionalFieldOf("light", Blocks.GLOWSTONE.defaultBlockState())
                    .forGetter(GemVeinDecorator::light),
            ThemeCodecs.BLOCK_STATE.optionalFieldOf("fallback", Blocks.TUFF.defaultBlockState())
                    .forGetter(GemVeinDecorator::fallback),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("anchor_chance", DEFAULT_ANCHOR_CHANCE)
                    .forGetter(GemVeinDecorator::anchorChance),
            Codec.LONG.optionalFieldOf("seed", 0x6E3A1L).forGetter(GemVeinDecorator::seed)
    ).apply(instance, GemVeinDecorator::new));

    @Override
    public MapCodec<? extends RiftDecorator> codec() {
        return CODEC;
    }

    public long salt() {
        return seed ^ 0x6E3A1E5L;
    }

    /** This theme's gem pool. */
    public List<RiftOres.Entry> pool() {
        return RiftOres.gemPool(home.orElse(null), host);
    }

    @Override
    public int decorate(DecoratorContext context, BlockState[] neighbourhood) {
        BlockState centre = neighbourhood[CENTRE];
        if (centre == null || !(centre.getBlock() instanceof GemSeamBlock)) return 0;
        int tier = context.facts().tier();
        BlockState out = switch (GemSeamBlock.role(centre)) {
            case LIGHT -> light;
            case CLUSTER -> RiftOres.ENABLED && context.roll() < RiftOres.clusterChance(tier)
                    ? RiftOres.amethystCluster() : Blocks.AIR.defaultBlockState();
            case GEM -> {
                Optional<BlockState> gem = RiftOres.ENABLED && context.roll() < RiftOres.gemFill(tier)
                        ? RiftOres.pick(pool(), tier, context.random()) : Optional.empty();
                yield gem.orElseGet(() -> host(neighbourhood));
            }
        };
        neighbourhood[CENTRE] = out;
        return CENTRE_BIT;
    }

    /** The first plain stone beside the seam, so an empty seam reads as the pocket wall. */
    private BlockState host(BlockState[] neighbourhood) {
        for (int side = 0; side < CENTRE; side++) {
            BlockState s = neighbourhood[side];
            if (s == null || s.isAir() || s.getBlock() instanceof GemSeamBlock || RiftOres.placedOre(s)
                    || RiftfallMarkers.isMarker(s) || s.hasBlockEntity() || !s.getProperties().isEmpty()) continue;
            if (s.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return s;
        }
        return fallback;
    }
}
