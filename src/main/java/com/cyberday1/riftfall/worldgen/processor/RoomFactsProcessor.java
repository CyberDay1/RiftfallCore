package com.cyberday1.riftfall.worldgen.processor;

import com.cyberday1.riftfall.init.RiftfallProcessors;
import com.cyberday1.riftfall.worldgen.decorator.RoomFacts;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Carries a placement's {@link RoomFacts} to the decorators on the native path, the way
 * {@link RoomKindProcessor} carries its kind. Changes no blocks. One instance per placement.
 */
public final class RoomFactsProcessor extends StructureProcessor {

    /** Never decoded from data: a datapack naming it gets tier 1 and no reservations. */
    public static final MapCodec<RoomFactsProcessor> CODEC = MapCodec.unit(() -> new RoomFactsProcessor(RoomFacts.NONE));

    private final RoomFacts facts;

    public RoomFactsProcessor(RoomFacts facts) {
        this.facts = facts == null ? RoomFacts.NONE : facts;
    }

    public RoomFacts facts() {
        return facts;
    }

    /** The facts stamped on this placement, or {@link RoomFacts#NONE}. */
    public static RoomFacts factsOf(@Nullable StructurePlaceSettings settings) {
        if (settings == null) return RoomFacts.NONE;
        List<StructureProcessor> processors = settings.getProcessors();
        for (int i = 0; i < processors.size(); i++) {
            if (processors.get(i) instanceof RoomFactsProcessor marker) return marker.facts();
        }
        return RoomFacts.NONE;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return RiftfallProcessors.ROOM_FACTS.get();
    }

    @Nullable
    @SuppressWarnings("deprecation")
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader world, BlockPos piecePos,
            BlockPos structurePos, StructureTemplate.StructureBlockInfo rawBlockInfo,
            StructureTemplate.StructureBlockInfo blockInfo, StructurePlaceSettings settings) {
        return blockInfo;
    }
}
