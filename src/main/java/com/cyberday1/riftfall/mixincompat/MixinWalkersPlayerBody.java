package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.compat.WalkersShapes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

/** A player wearing a Woodwalkers model keeps the player's own dimensions for every pose (MixinWalkersBody). */
@Mixin(Player.class)
public abstract class MixinWalkersPlayerBody {

    @Shadow
    @Final
    private static Map<Pose, EntityDimensions> POSES;

    @WrapMethod(method = "getDefaultDimensions")
    private EntityDimensions riftfall$playerDimensions(Pose pose, Operation<EntityDimensions> original) {
        return WalkersShapes.shaped((Player) (Object) this)
                ? POSES.getOrDefault(pose, Player.STANDING_DIMENSIONS) : original.call(pose);
    }
}
