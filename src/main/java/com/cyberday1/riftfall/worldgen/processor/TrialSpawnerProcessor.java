package com.cyberday1.riftfall.worldgen.processor;

import com.cyberday1.riftfall.block.marker.MarkerKind;
import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import com.cyberday1.riftfall.init.RiftfallProcessors;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Turns the library's authored {@code minecraft:trial_spawner} blocks into working rift spawners.
 *
 * <p>
 * 336 POI pieces carry one, every one of them with an empty {@code spawn_data.entity}, so as
 * authored they generate as vanilla trial spawners that name nothing to spawn. The block they become
 * is the one that does work: it waits for a player, makes a themed batch, and removes itself.
 *
 * <p>
 * The NBT is dropped rather than carried across. It is vanilla's trial-spawner block entity data and
 * {@link com.cyberday1.riftfall.block.marker.RiftMobSpawnerBlockEntity} saves and loads nothing, so
 * keeping it would only load a foreign compound onto a block entity that cannot read it.
 */
public final class TrialSpawnerProcessor extends StructureProcessor {

    /** The converting instance, which every real placement gets. */
    public static final TrialSpawnerProcessor CONVERT = new TrialSpawnerProcessor(true);

    /**
     * Withdraws the block instead of converting it, for {@code CorridorBlender}'s shifted copy of a
     * room: converting there would stand a second spawner a block off the one the room itself placed.
     * The same reason {@link BarrelConversionProcessor#DROP_MARKERS} exists.
     */
    public static final TrialSpawnerProcessor DROP = new TrialSpawnerProcessor(false);

    /** Never decoded from anything; the type is registered because a processor must have one. */
    public static final MapCodec<TrialSpawnerProcessor> CODEC = MapCodec.unit(() -> CONVERT);

    private final boolean convert;

    private TrialSpawnerProcessor(boolean convert) {
        this.convert = convert;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return RiftfallProcessors.TRIAL_SPAWNER.get();
    }

    /** Deprecated to steer callers, not to signal removal; see {@link ThemeSubstitutionProcessor}. */
    @Nullable
    @SuppressWarnings("deprecation")
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader world, BlockPos piecePos,
            BlockPos structurePos, StructureTemplate.StructureBlockInfo rawBlockInfo,
            StructureTemplate.StructureBlockInfo blockInfo, StructurePlaceSettings settings) {
        if (!blockInfo.state().is(Blocks.TRIAL_SPAWNER)) {
            return blockInfo;
        }
        if (!convert) {
            return null;
        }
        // Default state rather than the input's: the two blocks share property names but nothing
        // authored ever set them, and an armed spawner is the only state worth generating.
        return new StructureTemplate.StructureBlockInfo(blockInfo.pos(),
                RiftfallMarkers.block(MarkerKind.RIFT_MOB_SPAWNER).defaultBlockState(), null);
    }
}
