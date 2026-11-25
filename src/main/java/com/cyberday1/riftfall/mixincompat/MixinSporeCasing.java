package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.mob.SporeContainment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.Harbinger.Spore.Sentities.CasingGenerator", remap = false)
public interface MixinSporeCasing {
    // The Proto's biomass shell; the three-argument overload calls this one.
    @Inject(method = "generateChasing(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;II)V",
            at = @At("HEAD"), cancellable = true)
    default void riftfall$noCasingOutsideRifts(BlockPos pos, Entity entity, int radius, int thickness, CallbackInfo ci) {
        if (SporeContainment.contained(entity)) {
            SporeContainment.count("casing", null);
            ci.cancel();
        }
    }
}
