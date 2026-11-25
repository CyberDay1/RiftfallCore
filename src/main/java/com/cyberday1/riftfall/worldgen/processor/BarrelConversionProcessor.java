package com.cyberday1.riftfall.worldgen.processor;

import com.cyberday1.riftfall.block.barrel.BarrelTier;
import com.cyberday1.riftfall.block.barrel.RiftBarrelBlock;
import com.cyberday1.riftfall.block.barrel.RiftBarrels;
import com.cyberday1.riftfall.block.marker.MarkerKind;
import com.cyberday1.riftfall.block.marker.MarkerShapes;
import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import com.cyberday1.riftfall.init.RiftfallProcessors;
import com.cyberday1.riftfall.worldgen.barrel.BarrelRules;
import com.cyberday1.riftfall.worldgen.barrel.BarrelTally;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Turns the chest markers in an authored room into loot barrels, or into nothing.
 *
 * <p>
 * The rooms carry 3,335 {@code rift_chest} markers between them and a rift is meant to have a
 * handful of finds in it, so most of what this does is delete. A marker that loses the gate becomes
 * air rather than being left alone: the world underneath a room is the generator's bedrock filler,
 * so "leave it" would put a cube of it in the middle of the floor.
 *
 * <h2>Why a processor and not a pass over the finished room</h2>
 * There is no pass over a finished room to hook. A processor is where a template's blocks are
 * already being read one at a time, with the transform applied and the column's bounding box
 * already clipped, so every marker is offered exactly once and to the chunk that owns it.
 *
 * <h2>The one that does not convert</h2>
 * {@code CorridorBlender} stamps a room a second time, shifted a block, to lift a wall plane into a
 * seam. A marker in that plane would be rolled at a position a block off the real one and could put
 * a second barrel beside the first. {@link #DROP_MARKERS} is the instance that path gets: it
 * withdraws the block instead of converting it, leaving the blended wall the copy was made to place.
 */
public final class BarrelConversionProcessor extends StructureProcessor {

    /** Takes chest markers out of a placement without putting anything in their place. */
    public static final BarrelConversionProcessor DROP_MARKERS =
            new BarrelConversionProcessor(BarrelRules.NONE, 0L, null);

    /**
     * Never decoded from anything. A processor list in a datapack has no way to know a rift's seed,
     * so the only maker of these is {@code RiftChunkGenerator}; the type is registered because
     * {@link StructureProcessor} requires one, not because this is serialised.
     */
    public static final MapCodec<BarrelConversionProcessor> CODEC = MapCodec.unit(() -> DROP_MARKERS);

    private final BarrelRules rules;
    private final long riftSeed;

    /** Null on {@link #DROP_MARKERS}, and the flag that says this instance is the deleting one. */
    private final @Nullable BarrelTally tally;

    public BarrelConversionProcessor(BarrelRules rules, long riftSeed, @Nullable BarrelTally tally) {
        this.rules = rules;
        this.riftSeed = riftSeed;
        this.tally = tally;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return RiftfallProcessors.BARREL.get();
    }

    /** Deprecated to steer callers, not to signal removal; see {@link ThemeSubstitutionProcessor}. */
    @Nullable
    @SuppressWarnings("deprecation")
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader world, BlockPos piecePos,
            BlockPos structurePos, StructureTemplate.StructureBlockInfo rawBlockInfo,
            StructureTemplate.StructureBlockInfo blockInfo, StructurePlaceSettings settings) {
        BlockState input = blockInfo.state();
        if (input.getBlock() != RiftfallMarkers.block(MarkerKind.RIFT_CHEST)) {
            return blockInfo;
        }
        if (tally == null) {
            return null;
        }
        BlockPos pos = blockInfo.pos();
        // Process the full template for decorator context, but count only this column's writes.
        boolean inClip = settings == null || settings.getBoundingBox() == null
                || settings.getBoundingBox().isInside(pos);
        if (inClip) tally.consider();
        BarrelTier tier = rules.resolve(riftSeed, pos);
        if (tier == null) {
            return new StructureTemplate.StructureBlockInfo(pos, Blocks.AIR.defaultBlockState(), null);
        }
        if (inClip) tally.place(tier);

        // The marker's facing is horizontal and the barrel's is not, so this widens rather than
        // copies. The placement's own rotation and mirror are applied to the result afterwards.
        BlockState barrel = RiftBarrels.block(tier).defaultBlockState()
                .setValue(RiftBarrelBlock.FACING, input.getValue(MarkerShapes.RiftChestMarkerBlock.FACING));
        return new StructureTemplate.StructureBlockInfo(pos, barrel, null);
    }
}
