package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.rift.RiftLevels;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** This donor schedules its drop after death returns, outside RiftMobDrops' source context. */
@Pseudo
@Mixin(targets = "com.shipovskijkorp.scythes.mod.ability.BloodyEssenceDropHandler", remap = false)
public abstract class MixinScytheDeferredDrops {
    @Inject(method = "onKill", at = @At("HEAD"), cancellable = true)
    private static void riftfall$noRiftDeathReward(ServerLevel world, Entity killer, LivingEntity victim, CallbackInfo ci) {
        if (RiftLevels.isRiftWorld(world)) ci.cancel();
    }
}
