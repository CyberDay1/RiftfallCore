package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.mob.SporeCombat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"com.Harbinger.Spore.Sentities.Projectile.Vomit",
        "com.Harbinger.Spore.Sentities.Projectile.AcidBall"}, remap = false)
public abstract class MixinSporeCorrosion {
    @Inject(method = "levels", at = @At("HEAD"), cancellable = true)
    private void riftfall$boundedCorrosion(LivingEntity target, CallbackInfo ci) {
        if (!SporeCombat.managedProjectile((Entity) (Object) this)) return;
        BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("spore:corrosion"))
                .ifPresent(effect -> target.addEffect(new MobEffectInstance(effect, 100, 0)));
        ci.cancel();
    }
}
