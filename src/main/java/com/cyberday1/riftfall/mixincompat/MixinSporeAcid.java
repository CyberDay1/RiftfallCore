package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.mob.SporeEncounters;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.Harbinger.Spore.Sentities.Projectile.AcidBall", remap = false)
public abstract class MixinSporeAcid {
    @Inject(method = "place_acid", at = @At("HEAD"), cancellable = true)
    private static void riftfall$noAcidBlocks(LevelAccessor level, double x, double y, double z, CallbackInfo ci) {
        if (SporeEncounters.protectedLevel(level)) ci.cancel();
    }
}
