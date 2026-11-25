package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.home.HomeProtection;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Walking guests cannot operate pressure plates or tripwires. */
@Mixin(Entity.class)
public abstract class MixinHomeBlockTriggers {
    @Inject(method = "isIgnoringBlockTriggers", at = @At("HEAD"), cancellable = true)
    private void riftfall$guestTriggers(CallbackInfoReturnable<Boolean> cir) {
        var entity = (Entity) (Object) this;
        if (HomeProtection.denied(entity.level(), entity)) cir.setReturnValue(true);
    }
}
