package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.mob.SporeCombat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.Harbinger.Spore.Sentities.EvolvedInfected.Slasher", remap = false)
public abstract class MixinSporeSlasherDamage {
    @Inject(method = "amountOfDamage", at = @At("HEAD"), cancellable = true)
    private void riftfall$respectMitigation(float damage, CallbackInfoReturnable<Float> cir) {
        if (SporeCombat.managed((Entity) (Object) this)) cir.setReturnValue(0f);
    }
}
