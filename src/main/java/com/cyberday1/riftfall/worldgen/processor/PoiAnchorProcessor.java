package com.cyberday1.riftfall.worldgen.processor;

import com.cyberday1.riftfall.block.marker.PoiAnchorBlock;
import com.cyberday1.riftfall.block.marker.PoiAnchors;
import com.cyberday1.riftfall.init.RiftfallProcessors;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Takes the POI anchor blocks back out of a room and leaves their {@code final_state} behind.
 *
 * <p>
 * This is what vanilla's {@code JigsawReplacementProcessor} does for a real jigsaw, which is the
 * whole reason it is needed: an anchor block is read as a jigsaw at parse time but is an ordinary
 * block at placement time, so without this the finished room would have a cube of authoring marker
 * standing in the middle of the prop it asked for.
 *
 * <p>
 * It runs before the theme processors, on the same terms the jigsaw replacement does, so the
 * placeholder it leaves is themed like any other &mdash; a {@code free} anchor's floor comes out as
 * whatever that theme's floor is rather than as a bare placeholder.
 */
public final class PoiAnchorProcessor extends StructureProcessor {

    public static final PoiAnchorProcessor INSTANCE = new PoiAnchorProcessor();

    /**
     * Never decoded from anything. There is nothing to configure, so a datapack naming this would
     * get the singleton; the type is registered because {@link StructureProcessor} requires one.
     */
    public static final MapCodec<PoiAnchorProcessor> CODEC = MapCodec.unit(() -> INSTANCE);

    private PoiAnchorProcessor() {
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return RiftfallProcessors.POI_ANCHOR.get();
    }

    /** Deprecated to steer callers, not to signal removal; see {@link ThemeSubstitutionProcessor}. */
    @Nullable
    @SuppressWarnings("deprecation")
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader world, BlockPos piecePos,
            BlockPos structurePos, StructureTemplate.StructureBlockInfo rawBlockInfo,
            StructureTemplate.StructureBlockInfo blockInfo, StructurePlaceSettings settings) {
        if (!(blockInfo.state().getBlock() instanceof PoiAnchorBlock anchor)) {
            return blockInfo;
        }
        // final_state is a fixed block for every category, so the placement's rotation and mirror
        // have nothing to act on and the untransformed default state is the whole answer.
        return new StructureTemplate.StructureBlockInfo(blockInfo.pos(),
                PoiAnchors.finalState(anchor.kind()), null);
    }
}
