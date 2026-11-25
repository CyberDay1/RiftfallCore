package com.cyberday1.riftfall.worldgen.processor;

import com.cyberday1.riftfall.init.RiftfallProcessors;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/** Drops every block outside a box. First in a chain, it spares the rest of it the whole template. */
public final class ClipProcessor extends StructureProcessor {

    public static final MapCodec<ClipProcessor> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BoundingBox.CODEC.fieldOf("box").forGetter(ClipProcessor::box)
            ).apply(instance, ClipProcessor::new));

    private final BoundingBox box;

    public ClipProcessor(BoundingBox box) {
        this.box = box;
    }

    public BoundingBox box() {
        return box;
    }

    @Nullable
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader world, BlockPos piecePos,
            BlockPos structurePos, StructureTemplate.StructureBlockInfo rawBlockInfo,
            StructureTemplate.StructureBlockInfo blockInfo, StructurePlaceSettings settings) {
        return box.isInside(blockInfo.pos()) ? blockInfo : null;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return RiftfallProcessors.CLIP.get();
    }
}
