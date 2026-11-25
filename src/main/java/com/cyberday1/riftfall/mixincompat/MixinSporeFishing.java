package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.mob.SporeContainment;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.Harbinger.Spore.Sevents.HandlerEvents", remap = false)
public abstract class MixinSporeFishing {
    // 5% of open-water catches add an infected drowned with no spawn type; the catch itself is kept.
    @Inject(method = "FishingAnInfectedDrowned", at = @At("HEAD"), cancellable = true)
    private static void riftfall$noFishedInfected(ItemFishedEvent event, CallbackInfo ci) {
        if (event != null && SporeContainment.contained(event.getEntity())) ci.cancel();
    }
}
