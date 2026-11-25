package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.mob.SporeCombat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.Harbinger.Spore.Sentities.Projectile.ThrownTumor", remap = false)
public abstract class MixinSporeTumor {
    @Shadow public int duration;
    @Shadow public int potion_level;

    @Inject(method = "summonPotionEffects", at = @At("HEAD"))
    private void riftfall$boundedCloudEffect(CallbackInfo ci) {
        if (SporeCombat.managedProjectile((Entity) (Object) this)) {
            duration = Math.min(duration, 100);
            potion_level = 0;
        }
    }
}
