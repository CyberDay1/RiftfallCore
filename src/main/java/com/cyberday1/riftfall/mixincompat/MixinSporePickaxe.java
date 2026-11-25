package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.mob.SporeContainment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.Harbinger.Spore.Sitems.InfectedPickaxe", remap = false)
public abstract class MixinSporePickaxe {
    // The ore finder still points the way; it no longer infests the trail to the ore.
    @Inject(method = "changeBlock", at = @At("HEAD"), cancellable = true)
    private void riftfall$noInfestedTrail(Level level, BlockPos pos, CallbackInfo ci) {
        if (SporeContainment.contained(level)) ci.cancel();
    }
}
