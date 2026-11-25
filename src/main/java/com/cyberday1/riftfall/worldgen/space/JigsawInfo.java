package com.cyberday1.riftfall.worldgen.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One {@code minecraft:jigsaw} block read out of an authored template, with its block entity NBT
 * already decoded.
 *
 * <p>
 * This exists because vanilla 1.21.1 has no equivalent. {@code StructureTemplate.JigsawBlockInfo}
 * and {@code StructureTemplate.Palette#jigsaws()} arrived in 1.21.2; on 1.21.1 a jigsaw block is an
 * ordinary {@code StructureBlockInfo} whose state happens to be {@code minecraft:jigsaw} and whose
 * {@code nbt} carries the seven string/int tags below. Verified against
 * {@code neoforge-21.1.224-sources.jar}: {@code StructureTemplate.java} mentions neither symbol.
 *
 * <p>
 * Owning the record rather than adapting a vanilla one is the better shape anyway &mdash; the
 * layout only ever reads {@link #pool()} and {@link #front()}, and a local record does not move
 * under us on the next MC version.
 *
 * @param pos               block position, template-local, untransformed
 * @param state             the jigsaw blockstate, carrying the {@code orientation} property
 * @param name              this jigsaw's own name, matched by a parent's {@code target}
 * @param target            the name a child jigsaw must carry to connect here
 * @param pool              the template pool to draw a child from; the layout's only discriminator
 * @param finalState        what the jigsaw becomes once resolved, as an unparsed blockstate string
 * @param joint             rollable or aligned
 * @param placementPriority vanilla jigsaw ordering, carried through unused by this layout
 * @param selectionPriority vanilla jigsaw ordering, carried through unused by this layout
 */
public record JigsawInfo(BlockPos pos, BlockState state, ResourceLocation name, ResourceLocation target,
                         ResourceLocation pool, String finalState, JigsawBlockEntity.JointType joint,
                         int placementPriority, int selectionPriority) {

    /** The direction the jigsaw physically points in the authored file. */
    public Direction front() {
        return JigsawBlock.getFrontFacing(state);
    }

    /** The jigsaw's up-vector. Unused by layout; the prop pass needs it to orient wall-mounted pieces. */
    public Direction top() {
        return JigsawBlock.getTopFacing(state);
    }

    /** Block position converted to the section that contains it. */
    public net.minecraft.core.Vec3i section() {
        return new net.minecraft.core.Vec3i(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    /** How far up its own section this jigsaw sits: the other half of {@link #section()}. */
    public int elevation() {
        return pos.getY() & 15;
    }
}
