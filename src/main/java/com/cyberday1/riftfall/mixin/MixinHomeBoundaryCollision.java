package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.home.HomeBoundary;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.List;

@Mixin(Entity.class)
public abstract class MixinHomeBoundaryCollision {
    @WrapOperation(method = "collide", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<VoxelShape> riftfall$sectionWalls(Level level, Entity entity, AABB swept,
                                                  Operation<List<VoxelShape>> original) {
        return HomeBoundary.collisions(level, swept, original.call(level, entity, swept));
    }
}
