package com.cyberday1.riftfall.worldgen.async;

import com.cyberday1.riftfall.worldgen.space.TemplatePlacement;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import java.util.List;

/** WorldGenRegion saves these entities into their protochunk, before that chunk becomes live. */
public final class DeferredEntitySpawner {
    private DeferredEntitySpawner() {}
    public static void spawnAll(WorldGenLevel level, List<BakedRoom.BakedEntity> entities) {
        for (var baked : entities) {
            try {
                var tag = baked.nbt().copy();
                var pos = baked.position();
                var coordinates = new ListTag();
                coordinates.add(DoubleTag.valueOf(pos.x)); coordinates.add(DoubleTag.valueOf(pos.y)); coordinates.add(DoubleTag.valueOf(pos.z));
                tag.put("Pos", coordinates);
                tag.remove("UUID");
                EntityType.create(tag, level.getLevel()).ifPresent(entity -> {
                    float yaw = entity.rotate(TemplatePlacement.rotationOf(baked.mirror()));
                    yaw += entity.mirror(TemplatePlacement.mirrorOf(baked.mirror())) - entity.getYRot();
                    entity.moveTo(pos.x, pos.y, pos.z, yaw, entity.getXRot());
                    if (entity instanceof Mob mob)
                        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(pos)), MobSpawnType.STRUCTURE, null);
                    level.addFreshEntityWithPassengers(entity);
                });
            } catch (Exception e) {
                LogUtils.getLogger().warn("Cannot place authored rift entity at {}", baked.position(), e);
            }
        }
    }
}
