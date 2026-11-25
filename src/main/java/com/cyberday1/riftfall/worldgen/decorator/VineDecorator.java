package com.cyberday1.riftfall.worldgen.decorator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Grows vines on the air side of solid faces.
 *
 * <pre>
 * { "type": "riftfall:vine", "rarity": 0.20, "attach_to_wall": true, "attach_to_ceiling": true }
 * </pre>
 *
 * <p>
 * Unlike {@link AttachmentDecorator} this one visits the <em>solid</em> block and writes into its
 * air neighbours, because a vine's blockstate encodes which face it is stuck to — the decision and
 * the property both belong to the solid block, not to the air. It is the reason
 * {@link RiftDecorator}'s contract is a neighbourhood plus a bitmask rather than a single return
 * value.
 *
 * <p>
 * An air slot that already holds a vine gets the new face OR-ed in rather than replaced, so a vine
 * in a corner correctly ends up attached to both walls.
 */
public record VineDecorator(boolean attachToWall, boolean attachToCeiling, float rarity) implements RiftDecorator {

    public static final MapCodec<VineDecorator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("attach_to_wall", true).forGetter(VineDecorator::attachToWall),
            Codec.BOOL.optionalFieldOf("attach_to_ceiling", true).forGetter(VineDecorator::attachToCeiling),
            Codec.floatRange(0.0F, 1.0F).fieldOf("rarity").forGetter(VineDecorator::rarity)
    ).apply(instance, VineDecorator::new));

    private static final long SALT = 0x11AE5L;

    @Override
    public MapCodec<? extends RiftDecorator> codec() {
        return CODEC;
    }

    public long salt() {
        return SALT;
    }

    @Override
    public int decorate(DecoratorContext context, BlockState[] neighbourhood) {
        BlockState centre = neighbourhood[CENTRE];
        if (centre == null || centre.isAir() || centre.is(Blocks.VINE)) {
            return 0;
        }

        int changed = 0;
        if (attachToCeiling) {
            changed |= tryAttach(context, neighbourhood, Direction.DOWN);
        }
        if (attachToWall) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                changed |= tryAttach(context, neighbourhood, side);
            }
        }
        return changed;
    }

    /**
     * @param towards the direction from the centre block into the air slot we may fill; the vine
     *                then attaches to the centre, i.e. to {@code towards.getOpposite()}
     */
    private int tryAttach(DecoratorContext context, BlockState[] neighbourhood, Direction towards) {
        int index = towards.ordinal();
        BlockState target = neighbourhood[index];
        if (target == null) {
            return 0;
        }
        boolean existingVine = target.is(Blocks.VINE);
        if (!target.isAir() && !existingVine) {
            return 0;
        }
        if (context.roll() >= rarity) {
            return 0;
        }
        if (!DecoratorContext.isFaceSturdy(neighbourhood[CENTRE], towards)) {
            return 0;
        }

        BooleanProperty face = VineBlock.PROPERTY_BY_DIRECTION.get(towards.getOpposite());
        if (face == null) {
            // Vines have no DOWN property: a vine directly under a floor cannot exist.
            return 0;
        }
        BlockState base = existingVine ? target : Blocks.VINE.defaultBlockState();
        neighbourhood[index] = base.setValue(face, true);
        return 1 << index;
    }
}
