package com.cyberday1.riftfall.worldgen.async;

import com.cyberday1.riftfall.worldgen.barrel.BarrelRules;
import com.cyberday1.riftfall.worldgen.barrel.BarrelTally;
import com.cyberday1.riftfall.worldgen.space.TripleMirror;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** A bake is private to one future and published only when all processing succeeds. */
public record BakedRoom(List<StructureBlockInfo> writes, List<BakedEntity> entities,
                        List<BlockPos> barrelMarkers) {
    public record BakedEntity(Vec3 position, CompoundTag nbt, TripleMirror mirror) {}
    /** Count only successfully committed markers, never failed/saturated bakes. */
    public void recordBarrels(BarrelTally tally, BarrelRules rules, long seed) {
        for (BlockPos pos : barrelMarkers) {
            tally.consider();
            var tier = rules.resolve(seed, pos);
            if (tier != null) tally.place(tier);
        }
    }
}
